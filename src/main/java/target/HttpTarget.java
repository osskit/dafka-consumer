package target;

import configuration.Config;
import configuration.TopicsRoutes;
import dev.failsafe.RetryPolicy;
import dev.failsafe.okhttp.FailsafeCall;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import kafka.Producer;
import monitoring.Monitor;
import okhttp3.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONArray;
import org.json.JSONObject;

public class HttpTarget implements ITarget {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final TopicsRoutes topicsRoutes;

    private static final Duration httpTimeout = Duration.ofMillis(Config.TARGET_TIMEOUT_MS);

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .callTimeout(httpTimeout)
        .readTimeout(httpTimeout)
        .writeTimeout(httpTimeout)
        .connectTimeout(httpTimeout)
        .build();

    private final Producer producer;

    public HttpTarget(TopicsRoutes topicsRoutes, Producer producer) {
        this.topicsRoutes = topicsRoutes;
        this.producer = producer;
    }

    List<String> cloudEventHeaders = new ArrayList<>() {
        {
            add("ce_id");
            add("ce_time");
            add("ce_specversion");
            add("ce_type");
            add("ce_source");
        }
    };

    public CompletableFuture<Object> call(final ConsumerRecord<String, String> record) {
        var body = RequestBody.create(record.value(), JSON);

        var requestBuilder = new Request.Builder()
            .url(Config.TARGET_BASE_URL + this.topicsRoutes.getRoute(record.topic()))
            .post(body)
            .header("x-record-topic", record.topic())
            .header("x-record-partition", String.valueOf(record.partition()))
            .header("x-record-offset", String.valueOf(record.offset()))
            .header("x-record-timestamp", String.valueOf(record.timestamp()))
            .header("x-record-original-topic", this.getOriginalTopic(record));

        if (Config.ALLOW_BODY_HEADERS) {
            JSONObject jsonObject = new JSONObject(record.value());

            if (jsonObject.has("headers")) {
                JSONObject headersObject = jsonObject.getJSONObject("headers");

                for (String key : headersObject.keySet()) {
                    String value = headersObject.getString(key);

                    requestBuilder.header(key, value);
                }
            }
        }

        record
            .headers()
            .forEach(header -> {
                String headerKey = header.key();

                if (cloudEventHeaders.contains(headerKey)) {
                    headerKey = headerKey.replace("_", "-");
                }

                requestBuilder.header(headerKey, new String(header.value(), StandardCharsets.UTF_8));
            });

        final var request = requestBuilder.build();

        var call = client.newCall(request);
        var requestId = UUID.randomUUID().toString();

        var delay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(0);
        var maxDelay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(1);
        var delayFactor = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(2);
        var maxDuration = Duration.ofMillis(Config.RETRY_POLICY_MAX_DURATION_MS);

        Monitor.processMessageStarted(record, requestId);

        var retryPolicy = RetryPolicy
            .<Response>builder()
            .withBackoff(delay, maxDelay, ChronoUnit.MILLIS, delayFactor)
            .handleIf(e -> true)
            .handleResultIf(r -> Integer.toString(r.code()).matches(Config.RETRY_PROCESS_WHEN_STATUS_CODE_MATCH))
            .withMaxDuration(maxDuration)
            .onRetry(e -> {
                Monitor.targetExecutionRetry(
                    Optional
                        .ofNullable(e.getLastResult())
                        .flatMap(r -> {
                            try {
                                var responseBody = r.body().string();
                                return Optional.of(responseBody);
                            } catch (IOException error) {
                                return Optional.empty();
                            }
                        }),
                    e.getLastException(),
                    e.getAttemptCount(),
                    requestId
                );
            })
            .build();

        final long executionStart = (new Date()).getTime();

        return FailsafeCall
            .with(retryPolicy)
            .compose(call)
            .executeAsync()
            .handleAsync((response, throwable) -> {
                if (throwable != null) {
                    Monitor.processMessageError(throwable, requestId);
                    if (Config.RETRY_TOPIC != null) {
                        Monitor.retryProduced(Config.RETRY_TOPIC, requestId);
                        return producer.produce(Config.RETRY_TOPIC, record, requestId);
                    }
                    return CompletableFuture.failedFuture(throwable);
                }

                var statusCode = Integer.toString(response.code());

                if (statusCode.matches(Config.PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                    if (Config.RETRY_TOPIC != null) {
                        Monitor.retryProduced(Config.RETRY_TOPIC, requestId);
                        return producer.produce(Config.RETRY_TOPIC, record, requestId);
                    }
                }

                if (statusCode.matches(Config.PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                    if (Config.DEAD_LETTER_TOPIC != null) {
                        Monitor.deadLetterProduced(Config.DEAD_LETTER_TOPIC, requestId);
                        return producer.produce(Config.DEAD_LETTER_TOPIC, record, requestId);
                    }
                }

                Monitor.processMessageSuccess(requestId, executionStart);

                return CompletableFuture.completedFuture(null);
            });
    }
}
