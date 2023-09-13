package target;

import configuration.Config;
import configuration.TopicsRoutes;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kafka.Producer;
import monitoring.Monitor;
import okhttp3.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;

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

    public CompletableFuture<Object> call(final ConsumerRecord<String, String> record) {
        var requestId = UUID.randomUUID().toString();
        Monitor.processMessageStarted(record, requestId);
        try {
            return TargetRetryPolicy
                .create(record, requestId)
                .compose(client.newCall(createRequest(record)))
                .executeAsync()
                .handleAsync((response, throwable) ->
                    onExecutionSuccess(response, throwable, record, (new Date()).getTime(), requestId)
                );
        } catch (Throwable throwable) {
            Monitor.processMessageError(throwable, requestId);
            if (Config.DEAD_LETTER_TOPIC != null) {
                Monitor.deadLetterProduced(Config.DEAD_LETTER_TOPIC, requestId);
                return producer.produce(Config.DEAD_LETTER_TOPIC, record, requestId);
            }
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private Request createRequest(final ConsumerRecord<String, String> record) {
        var requestBuilder = new Request.Builder()
            .url(Config.TARGET_BASE_URL + this.topicsRoutes.getRoute(record.topic()))
            .post(RequestBody.create(record.value(), MediaType.get("application/json; charset=utf-8")))
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
        return requestBuilder.build();
    }

    private CompletableFuture<Object> onExecutionSuccess(
        Response response,
        Throwable throwable,
        ConsumerRecord<String, String> record,
        long executionStart,
        String requestId
    ) {
        try (Response r = response) {
            if (throwable != null) {
                Monitor.processMessageCompleted(requestId, executionStart, -1, throwable);
                if (Config.RETRY_TOPIC != null) {
                    Monitor.retryProduced(Config.RETRY_TOPIC, requestId);
                    return producer.produce(Config.RETRY_TOPIC, record, requestId);
                }
                return CompletableFuture.failedFuture(throwable);
            }

            Monitor.processMessageCompleted(requestId, executionStart, r.code(), null);
            if (
                Integer.toString(r.code()).matches(Config.PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH) &&
                Config.RETRY_TOPIC != null
            ) {
                Monitor.retryProduced(Config.RETRY_TOPIC, requestId);
                return producer.produce(Config.RETRY_TOPIC, record, requestId);
            }

            if (
                Integer.toString(r.code()).matches(Config.PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH) &&
                Config.DEAD_LETTER_TOPIC != null
            ) {
                Monitor.deadLetterProduced(Config.DEAD_LETTER_TOPIC, requestId);
                return producer.produce(Config.DEAD_LETTER_TOPIC, record, requestId);
            }

            return CompletableFuture.completedFuture(null);
        }
    }
}
