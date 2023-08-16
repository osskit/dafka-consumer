package monitoring;

import configuration.Config;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import java.util.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.json.JSONObject;

public class Monitor {

    private static Counter processMessageStarted;
    private static Counter processMessageSuccess;
    private static Counter processMessageError;
    private static Counter retryProduced;
    private static Counter deadLetterProduced;
    private static Counter produceError;
    private static Counter targetExecutionRetry;
    private static Counter targetExecutionRetrySuccess;
    private static Counter connectionFailureRetry;
    private static Counter connectionFailureRetrySuccess;
    private static Histogram messageLatency;
    private static Histogram processBatchExecutionTime;
    private static Histogram processMessageExecutionTime;
    private static Histogram callTargetLatency;
    private static Histogram resultTargetLatency;
    private static Gauge assignedPartitions;

    private static double[] buckets = new double[0];

    public static void init() {
        if (Config.PROMETHEUS_BUCKETS != null) {
            buckets =
                Arrays
                    .asList(Config.PROMETHEUS_BUCKETS.split(","))
                    .stream()
                    .mapToDouble(s -> Double.parseDouble(s))
                    .toArray();
        }

        assignedPartitions = Gauge.build().name("assigned_partitions").help("assigned_partitions").register();

        messageLatency =
            Histogram
                .build()
                .buckets(buckets)
                .labelNames("topic")
                .name("message_latency")
                .help("message_latency")
                .register();

        callTargetLatency =
            Histogram.build().buckets(buckets).name("call_target_latency").help("call_target_latency").register();

        resultTargetLatency =
            Histogram.build().buckets(buckets).name("result_target_latency").help("result_target_latency").register();

        processMessageSuccess =
            Counter.build().name("process_message_success").help("process_message_success").register();

        processMessageError = Counter.build().name("process_message_error").help("process_message_error").register();

        processBatchExecutionTime =
            Histogram
                .build()
                .buckets(buckets)
                .name("process_batch_execution_time")
                .help("process_batch_execution_time")
                .register();

        processMessageExecutionTime =
            Histogram
                .build()
                .buckets(buckets)
                .name("process_message_execution_time")
                .help("process_message_execution_time")
                .register();

        processMessageStarted =
            Counter.build().name("process_message_started").help("process_message_started").register();

        retryProduced = Counter.build().name("retry_produced").help("retry_produced").register();

        deadLetterProduced = Counter.build().name("dead_letter_produced").help("dead_letter_produced").register();

        produceError = Counter.build().name("produce_error").help("produce_error").register();

        targetExecutionRetry =
            Counter
                .build()
                .name("target_execution_retry")
                .labelNames("attempt")
                .help("target_execution_retry")
                .register();

        connectionFailureRetry =
            Counter
                .build()
                .name("connection_failure_retry")
                .labelNames("attempt")
                .help("connection_failure_retry")
                .register();
    }

    public static void waitingForTargetHealthcheck() {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "waiting for target healthcheck")
            .put(
                "extra",
                new JSONObject()
                    .put("targetBaseUrl", Config.TARGET_BASE_URL)
                    .put("targetHealthcheck", Config.TARGET_HEALTHCHECK)
            );

        write(log);
    }

    public static void targetHealthcheckPassedSuccessfully() {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "target healthcheck passed successfully")
            .put(
                "extra",
                new JSONObject()
                    .put("targetBaseUrl", Config.TARGET_BASE_URL)
                    .put("targetHealthcheck", Config.TARGET_HEALTHCHECK)
            );

        write(log);
    }

    public static void batchProcessStarted(int count) {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "batch process started")
            .put("extra", new JSONObject().put("count", count));

        write(log);
    }

    public static void batchProcessCompleted(Long batchStartTimestamp) {
        var executionTimeMs = new Date().getTime() - batchStartTimestamp;
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "batch process completed")
            .put("extra", new JSONObject().put("executionTime", executionTimeMs));

        write(log);
        processBatchExecutionTime.observe((double) executionTimeMs / 1000);
    }

    public static void processMessageStarted(ConsumerRecord<String, String> record, String requestId) {
        messageLatency.labels(record.topic()).observe(((double) (new Date().getTime() - record.timestamp())) / 1000);
        processMessageStarted.inc();

        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "process message started")
                .put(
                    "extra",
                    new JSONObject()
                        .put(
                            "record",
                            new JSONObject()
                                .put("value", record.value())
                                .put("topic", record.topic())
                                .put("partition", record.partition())
                                .put("offset", record.offset())
                                .put("headers", record.headers())
                                .put("key", record.key())
                        )
                        .put("requestId", requestId)
                )
        );
    }

    public static void processMessageSuccess(String requestId, long executionStart) {
        var extra = new JSONObject();
        extra.put("requestId", requestId);
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "process message success")
            .put("extra", extra);
        write(log);
        processMessageExecutionTime.observe(((double) (new Date().getTime() - executionStart)) / 1000);
        processMessageSuccess.inc();
    }

    public static void processMessageError(Throwable exception, String requestId) {
        var extra = new JSONObject();
        extra.put(
            "err",
            new JSONObject()
                .put("errorMessages", getErrorMessages(exception))
                .put("class", exception.getClass())
                .put("stacktrace", exception.getStackTrace())
        );
        extra.put("requestId", requestId);
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "process message failed")
            .put("extra", extra);
        write(log);
        processMessageError.inc();
    }

    public static void retryProduced(String topic, String requestId) {
        var extra = new JSONObject();
        extra.put("requestId", requestId);
        extra.put("topic", topic);
        JSONObject log = new JSONObject().put("level", "info").put("message", "retry produced").put("extra", extra);
        write(log);

        retryProduced.inc();
    }

    public static void deadLetterProduced(String topic, String requestId) {
        var extra = new JSONObject();
        extra.put("requestId", requestId);
        extra.put("topic", topic);
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "dead letter produced")
            .put("extra", extra);

        write(log);

        deadLetterProduced.inc();
    }

    public static void consumerError(Throwable exception) {
        JSONObject log = new JSONObject()
            .put("level", "error")
            .put("message", "consumer stream was terminated due to unexpected error")
            .put(
                "err",
                new JSONObject()
                    .put("errorMessages", getErrorMessages(exception))
                    .put("class", exception.getClass())
                    .put("stacktrace", exception.getStackTrace())
            );

        write(log);
    }

    public static void commitFailed(Throwable exception) {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "commit failed")
            .put(
                "err",
                new JSONObject()
                    .put("errorMessages", getErrorMessages(exception))
                    .put("class", exception.getClass())
                    .put("stacktrace", exception.getStackTrace())
            );

        write(log);
    }

    public static void consumerCompleted() {
        JSONObject log = new JSONObject().put("level", "error").put("message", "consumer stream was completed");
        write(log);
    }

    public static void shuttingDown() {
        JSONObject log = new JSONObject().put("level", "info").put("message", "shutting down");
        write(log);
    }

    public static void initializationError(Throwable exception) {
        JSONObject log = new JSONObject()
            .put("level", "error")
            .put("message", "Unexpected error while initializing")
            .put(
                "err",
                new JSONObject().put("errorMessages", getErrorMessages(exception)).put("class", exception.getClass())
            );

        write(log);
    }

    public static void started() {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "kafka-consumer-" + Config.GROUP_ID + " started");

        write(log);
    }

    public static void assignedToPartition(Collection<TopicPartition> partitions) {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "consumer was assigned to partitions")
            .put("extra", new JSONObject().put("count", partitions.size()));

        write(log);
        assignedPartitions.inc(partitions.size());
    }

    public static void revokedFromPartition(Collection<TopicPartition> partitions) {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "consumer was revoked from partitions")
            .put("extra", new JSONObject().put("count", partitions.size()));

        write(log);
        assignedPartitions.dec(partitions.size());
    }

    public static void serviceTerminated() {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "kafka-consumer-" + Config.GROUP_ID + " terminated");

        write(log);
    }

    public static void produceError(String topic, String requestId, Throwable exception) {
        var extra = new JSONObject();
        extra.put("requestId", requestId);
        JSONObject log = new JSONObject()
            .put("level", "error")
            .put("message", String.format("failed producing message to %s topic", topic))
            .put("extra", extra)
            .put("err", new JSONObject().put("message", exception.getMessage()));

        write(log);

        produceError.inc();
    }

    private static void logTargetRetry(
        Optional<String> responseBody,
        Throwable exception,
        String requestId,
        String message
    ) {
        var extra = new JSONObject();
        extra.put("requestId", requestId);
        if (responseBody.isPresent()) {
            extra.put("response", responseBody.get());
        }

        var error = new JSONObject();
        if (exception != null) {
            error.put("message", exception.getMessage());
            error.put("type", exception.getClass());
        }

        JSONObject log = new JSONObject().put("level", "info").put("message", message);

        log.put("extra", extra);
        log.put("err", error);

        write(log);
    }

    private static void logTargetRetrySuccess(Optional<String> responseBody, String requestId, String message) {
        var extra = new JSONObject();
        extra.put("requestId", requestId);
        if (responseBody.isPresent()) {
            extra.put("response", responseBody.get());
        }

        var error = new JSONObject();

        JSONObject log = new JSONObject().put("level", "info").put("message", message);

        log.put("extra", extra);
        log.put("err", error);

        write(log);
    }

    public static void targetExecutionRetry(
        Optional<String> responseBody,
        Throwable exception,
        int attempt,
        String requestId
    ) {
        logTargetRetry(responseBody, exception, requestId, "target retry");
        targetExecutionRetry.labels(String.valueOf(attempt)).inc();
    }

    public static void targetExecutionRetrySuccess(Optional<String> responseBody, int attempt, String requestId) {
        logTargetRetrySuccess(responseBody, requestId, "target retry succeeded");
        targetExecutionRetrySuccess.labels(String.valueOf(attempt)).inc();
    }

    public static void connectionFailureRetrySuccess(Optional<String> responseBody, int attempt, String requestId) {
        logTargetRetrySuccess(responseBody, requestId, "connection failure retry succeeded");
        connectionFailureRetrySuccess.labels(String.valueOf(attempt)).inc();
    }

    public static void connectionFailureRetry(
        Optional<String> responseBody,
        Throwable exception,
        int attempt,
        String requestId
    ) {
        logTargetRetry(responseBody, exception, requestId, "connection failure retry");
        connectionFailureRetry.labels(String.valueOf(attempt)).inc();
    }

    public static void targetHealthcheckFailed(Exception exception) {
        JSONObject log = new JSONObject()
            .put("level", "info")
            .put("message", "target healthcheck failed")
            .put("exceptionMessage", exception.getMessage());

        write(log);
    }

    private static void write(JSONObject log) {
        System.out.println(log.toString());
    }

    private static ArrayList<String> getErrorMessagesArray(Throwable exception, ArrayList<String> messages) {
        if (exception == null) {
            return messages;
        }
        messages.add(exception.getMessage());
        return getErrorMessagesArray(exception.getCause(), messages);
    }

    private static JSONObject getErrorMessages(Throwable exception) {
        var messages = getErrorMessagesArray(exception, new ArrayList<String>());
        var errorMessages = new JSONObject();
        for (var i = 0; i < messages.size(); i++) {
            errorMessages.put("message" + i, messages.get(i));
        }
        return errorMessages;
    }
}
