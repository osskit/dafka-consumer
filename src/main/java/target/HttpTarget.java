package target;

import configuration.Config;
import configuration.TopicsRoutes;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.okhttp.FailsafeCall;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import kafka.Producer;
import monitoring.Monitor;
import okhttp3.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class HttpTarget implements ITarget {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final TargetRetryPolicy retryPolicy;
    private final TopicsRoutes topicsRoutes;
    private final OkHttpClient client = new OkHttpClient.Builder()
        .callTimeout(Duration.ofMillis(Config.TARGET_TIMEOUT_MS))
        .build();

    private final Producer producer;

    public HttpTarget(final TargetRetryPolicy retryPolicy, TopicsRoutes topicsRoutes, Producer producer) {
        this.retryPolicy = retryPolicy;
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

    public CompletableFuture<TargetResponse> call(final ConsumerRecord<String, String> record) {
        var body = RequestBody.create(record.value(), JSON);

        var requestBuilder = new Request.Builder()
            .url(Config.TARGET_BASE_URL + this.topicsRoutes.getRoute(record.topic()))
            .post(body)
            .header("x-record-topic", record.topic())
            .header("x-record-partition", String.valueOf(record.partition()))
            .header("x-record-offset", String.valueOf(record.offset()))
            .header("x-record-timestamp", String.valueOf(record.timestamp()))
            .header("x-record-original-topic", this.getOriginalTopic(record));

        record
            .headers()
            .forEach(header -> {
                String headerKey = header.key();

                    if (cloudEventHeaders.contains(headerKey)) {
                        headerKey = headerKey.replace("_", "-");
                    }

                    requestBuilder.header(headerKey, new String(header.value(), StandardCharsets.UTF_8));
                }

                builder.header(headerKey, new String(header.value(), StandardCharsets.UTF_8));
            });

        final var request = requestBuilder.build();

        var call = client.newCall(request);

        var delay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(0);
        var maxDelay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(1);
        var delayFactor = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(2);
        var maxDuration = Duration.ofMillis(Config.RETRY_POLICY_MAX_DURATION_MS);

        var retryPolicy = RetryPolicy
            .<Response>builder()
            .withBackoff(delay, maxDelay, ChronoUnit.MILLIS, delayFactor)
            .handleIf(e -> true)
            .handleResultIf(r -> Integer.toString(r.code()).matches(Config.RETRY_PROCESS_WHEN_STATUS_CODE_MATCH))
            .withMaxDuration(maxDuration)
            .build();

        return FailsafeCall
            .with(retryPolicy)
            .compose(call)
            .executeAsync()
            .handleAsync(
                (response, throwable) -> {
                    if (throwable != null && Config.RETRY_TOPIC != null) {
                        return producer.produce(Config.RETRY_TOPIC, record);
                    }

                    var statusCode = Integer.toString(response.code());

                    if (statusCode.matches(Config.PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                        Monitor.processMessageError();
                        if (Config.RETRY_TOPIC != null) {
                            producer.produce(Config.RETRY_TOPIC, record);
                            Monitor.retryProduced(record);
                            return;
                        }
                    }

                    if (statusCode.matches(Config.PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                        Monitor.processMessageError();
                        if (Config.DEAD_LETTER_TOPIC != null) {
                            producer.produce(Config.DEAD_LETTER_TOPIC, record);
                            Monitor.deadLetterProcdued(record);
                        }
                        return;
                    }
                }
            );
    }
}
