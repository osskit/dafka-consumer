package target;

import configuration.Config;
import configuration.TopicsRoutes;
import dev.failsafe.RetryPolicy;
import dev.failsafe.okhttp.FailsafeCall;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import kafka.Producer;
import monitoring.LegacyMonitor;
import monitoring.Monitor;
import okhttp3.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class HttpTarget implements ITarget {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static Counter targetExecutionRetry;

    private final TopicsRoutes topicsRoutes;

    private static final Logger logger = LoggerFactory.getLogger(HttpTarget.class);

    private static final OkHttpClient client = new OkHttpClient.Builder()
        .callTimeout(Duration.ofMillis(Config.TARGET_TIMEOUT_MS))
        .build();

    private final Histogram callTargetLatency;

    private final Histogram resultTargetLatency;

    private final Producer producer;
    private final Monitor monitor;

    public HttpTarget(TopicsRoutes topicsRoutes, Producer producer) {
        this.topicsRoutes = topicsRoutes;
        this.producer = producer;
        var labels = new ArrayList<String>();
        labels.add("topic");
        this.monitor = new Monitor("httpTarget", labels);

        targetExecutionRetry =
            Counter
                .build()
                .name("target_execution_retry")
                .labelNames("attempt", "topic")
                .help("target_execution_retry")
                .register();

        double[] buckets = new double[0];

        if (Config.PROMETHEUS_BUCKETS != null) {
            buckets =
                Arrays
                    .asList(Config.PROMETHEUS_BUCKETS.split(","))
                    .stream()
                    .mapToDouble(s -> Double.parseDouble(s))
                    .toArray();
        }

        callTargetLatency =
            Histogram.build().buckets(buckets).name("call_target_latency").help("call_target_latency").register();
        resultTargetLatency =
            Histogram.build().buckets(buckets).name("result_target_latency").help("result_target_latency").register();
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

        var delay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(0);
        var maxDelay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(1);
        var delayFactor = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(2);
        var maxDuration = Duration.ofMillis(Config.RETRY_POLICY_MAX_DURATION_MS);

        var labels = new ArrayList<String>();
        labels.add(record.topic());
        var context = new HashMap<String, String>();
        context.put("topic", record.topic());
        context.put("key", record.key());
        context.put("value", record.value());

        var retryPolicy = RetryPolicy
            .<Response>builder()
            .withBackoff(delay, maxDelay, ChronoUnit.MILLIS, delayFactor)
            .handleIf(e -> true)
            .handleResultIf(r -> Integer.toString(r.code()).matches(Config.RETRY_PROCESS_WHEN_STATUS_CODE_MATCH))
            .withMaxDuration(maxDuration)
            .onRetry(e -> {
                var attemptCount = Integer.toString(e.getAttemptCount());
                MDC.put("attempt_count", attemptCount);

                context.forEach((key, value) -> MDC.put(key, value));

                if (e.getLastException() != null) {
                    logger.info("attempt failed", e.getLastException());
                } else {
                    var response = e.getLastResult();
                    MDC.put("status_code", Integer.toString(response.code()));
                    MDC.put("response_body", response.body().string());

                    logger.info("attempt failed");
                }
                MDC.clear();
                targetExecutionRetry.labels(attemptCount, record.topic()).inc();
            })
            .build();

        final long startTime = (new Date()).getTime();

        return this.monitor.monitor(
                "call",
                labels,
                context,
                FailsafeCall.with(retryPolicy).compose(call).executeAsync()
            )
            .handleAsync((response, throwable) -> {
                if (throwable != null) {
                    if (Config.RETRY_TOPIC != null) {
                        return producer.produce(Config.RETRY_TOPIC, record);
                    }

                    return CompletableFuture.failedFuture(throwable);
                }

                var statusCode = Integer.toString(response.code());

                if (statusCode.matches(Config.PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                    LegacyMonitor.processMessageError();
                    if (Config.RETRY_TOPIC != null) {
                        return producer.produce(Config.RETRY_TOPIC, record);
                    }
                }

                if (statusCode.matches(Config.PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                    LegacyMonitor.processMessageError();
                    if (Config.DEAD_LETTER_TOPIC != null) {
                        return producer.produce(Config.DEAD_LETTER_TOPIC, record);
                    }
                }

                var callLatency = response.header("x-received-timestamp");
                var resultLatency = response.header("x-completed-timestamp");
                if (callLatency != null) {
                    callTargetLatency.observe(Long.getLong(callLatency) - startTime);
                }

                if (resultLatency != null) {
                    resultTargetLatency.observe(Long.getLong(resultLatency) - startTime);
                }

                return CompletableFuture.completedFuture(null);
            });
    }
}
