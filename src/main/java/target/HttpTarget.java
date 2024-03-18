package target;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import configuration.Config;
import configuration.TopicsRoutes;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import kafka.Producer;
import monitoring.Monitor;
import okhttp3.*;
import org.json.JSONObject;
import reactor.kafka.receiver.ReceiverRecord;

public class HttpTarget implements ITarget {

    private final TopicsRoutes topicsRoutes;
    private static final Duration httpTimeout = Duration.ofMillis(Config.TARGET_TIMEOUT_MS);
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .callTimeout(httpTimeout)
        .readTimeout(httpTimeout)
        .writeTimeout(httpTimeout)
        .connectTimeout(httpTimeout)
        .connectionPool(
            new ConnectionPool(
                Config.CONNECTION_POOL_MAX_IDLE_CONNECTIONS,
                Config.CONNECTION_POOL_KEEP_ALIVE_DURATION_MS,
                TimeUnit.MILLISECONDS
            )
        )
        .build();

    private final Producer producer;

    public HttpTarget(TopicsRoutes topicsRoutes, Producer producer) {
        this.topicsRoutes = topicsRoutes;
        this.producer = producer;
    }

    public CompletableFuture<Object> call(
        final ReceiverRecord<String, String> record,
        String batchRequestId,
        String targetRequestId
    ) {
        Monitor.processMessageStarted(record, batchRequestId, targetRequestId);
        try {
            return TargetRetryPolicy
                .create(batchRequestId, targetRequestId)
                .compose(client.newCall(createRequest(record)))
                .executeAsync()
                .handleAsync((response, throwable) ->
                    onExecutionSuccess(
                        response,
                        throwable,
                        record,
                        (new Date()).getTime(),
                        batchRequestId,
                        targetRequestId
                    )
                );
        } catch (Throwable throwable) {
            Monitor.processMessageError(record, throwable, batchRequestId, targetRequestId);
            if (Config.DEAD_LETTER_TOPIC != null) {
                Monitor.deadLetterProduced(record, batchRequestId, targetRequestId);
                return producer.produce(
                    Config.DEAD_LETTER_TOPIC,
                    record,
                    Optional.empty(),
                    Optional.of(throwable),
                    batchRequestId,
                    targetRequestId
                );
            }
            return CompletableFuture.failedFuture(throwable);
        }
    }

    @Override
    public CompletableFuture<Object> callBatch(
        List<ReceiverRecord<String, String>> records,
        String batchRequestId,
        String targetRequestId
    ) {
        var gson = new Gson();
        var body = !Config.RECORD_PICK_FIELD.isEmpty()
            ? gson.toJson(
                (
                    records
                        .stream()
                        .map(r -> gson.fromJson(r.value(), JsonElement.class).getAsJsonObject())
                        .map(x -> x.get(Config.RECORD_PICK_FIELD))
                        .collect(Collectors.toList())
                )
            )
            : records.stream().map(ReceiverRecord::value).toList().toString();

        try {
            var last = records.get(records.size() - 1);
            var request = new Request.Builder()
                .url(Config.TARGET_BASE_URL + this.topicsRoutes.getRoute(last.topic()))
                .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")))
                .build();

            return TargetRetryPolicy
                .create(batchRequestId, targetRequestId)
                .compose(client.newCall(request))
                .executeAsync()
                .handleAsync((response, throwable) ->
                    Integer
                            .toString(response.code())
                            .matches(Config.PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH) ||
                        throwable != null
                        ? new TargetException(response, throwable)
                        : null
                );
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private Request createRequest(final ReceiverRecord<String, String> record) {
        var gson = new Gson();

        var body = !Config.RECORD_PICK_FIELD.isEmpty()
            ? gson.toJson(
                (gson.fromJson(record.value(), JsonElement.class).getAsJsonObject().get(Config.RECORD_PICK_FIELD))
            )
            : record.value();

        var requestBuilder = new Request.Builder()
            .url(Config.TARGET_BASE_URL + this.topicsRoutes.getRoute(record.topic()))
            .post(RequestBody.create(body, MediaType.get("application/json; charset=utf-8")))
            .header("x-record-topic", record.topic())
            .header("x-record-partition", String.valueOf(record.partition()))
            .header("x-record-offset", String.valueOf(record.offset()))
            .header("x-record-timestamp", String.valueOf(record.timestamp()))
            .header("x-record-original-topic", this.getOriginalTopic(record));

        if (Config.BODY_HEADERS_PATHS != null) {
            var jsonObject = new JSONObject(record.value());
            Config.BODY_HEADERS_PATHS.forEach(key -> {
                if (jsonObject.has(key)) {
                    JSONObject headersObject = jsonObject.getJSONObject(key);

                    for (String headerKey : headersObject.keySet()) {
                        if (!headersObject.isNull(headerKey)) {
                            String value = headersObject.getString(headerKey);
                            requestBuilder.header(headerKey, value);
                        }
                    }
                }
            });
        }

        record
            .headers()
            .forEach(header -> {
                String headerKey = header.key();
                requestBuilder.header(headerKey, new String(header.value(), StandardCharsets.UTF_8));
            });
        return requestBuilder.build();
    }

    private CompletableFuture<Object> onExecutionSuccess(
        Response response,
        Throwable throwable,
        ReceiverRecord<String, String> record,
        long executionStart,
        String batchRequestId,
        String targetRequestId
    ) {
        try (Response r = response) {
            if (throwable != null) {
                Monitor.processMessageCompleted(record, batchRequestId, targetRequestId, executionStart, -1, throwable);
                if (Config.DEAD_LETTER_TOPIC != null) {
                    Monitor.deadLetterProduced(record, batchRequestId, targetRequestId);
                    return producer.produce(
                        Config.DEAD_LETTER_TOPIC,
                        record,
                        Optional.empty(),
                        Optional.of(throwable),
                        batchRequestId,
                        targetRequestId
                    );
                }
                return CompletableFuture.failedFuture(throwable);
            }
            Monitor.processMessageCompleted(record, batchRequestId, targetRequestId, executionStart, r.code(), null);

            if (
                Integer.toString(r.code()).matches(Config.PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH) &&
                Config.DEAD_LETTER_TOPIC != null
            ) {
                Monitor.deadLetterProduced(record, batchRequestId, targetRequestId);
                return producer.produce(
                    Config.DEAD_LETTER_TOPIC,
                    record,
                    Optional.of(r),
                    Optional.empty(),
                    batchRequestId,
                    targetRequestId
                );
            }

            return CompletableFuture.completedFuture(null);
        }
    }
}
