package monitoring;

import configuration.Config;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import reactor.kafka.receiver.ReceiverPartition;

public class Monitor {

    private static Counter processMessageStarted;
    private static Counter processMessageSuccess;
    private static Counter processMessageError;
    private static Counter deadLetterProduced;
    private static Counter produceError;
    private static Counter targetExecutionRetry;
    private static Counter targetConnectionRetry;
    private static Histogram messageLatency;
    private static Histogram processBatchExecutionTime;
    private static Histogram processMessageExecutionTime;
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

        deadLetterProduced = Counter.build().name("dead_letter_produced").help("dead_letter_produced").register();

        produceError = Counter.build().name("produce_error").help("produce_error").register();

        targetExecutionRetry =
            Counter
                .build()
                .name("target_execution_retry")
                .labelNames("attempt")
                .help("target_execution_retry")
                .register();

        targetConnectionRetry =
            Counter
                .build()
                .name("target_connection_retry")
                .labelNames("attempt")
                .help("target_connection_retry")
                .register();
    }

    public static void waitingForTargetHealthcheck() {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "waiting for target healthcheck")
                .put(
                    "extra",
                    new JSONObject()
                        .put("targetBaseUrl", Config.TARGET_BASE_URL)
                        .put("targetHealthCheck", Config.TARGET_HEALTHCHECK)
                )
        );
    }

    public static void targetHealthcheckPassedSuccessfully() {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "target healthcheck passed successfully")
                .put(
                    "extra",
                    new JSONObject()
                        .put("targetBaseUrl", Config.TARGET_BASE_URL)
                        .put("targetHealthCheck", Config.TARGET_HEALTHCHECK)
                )
        );
    }

    public static void batchProcessStarted(String requestId) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "batch process started")
                .put("extra", new JSONObject().put("batchRequestId", requestId))
        );
    }

    public static void batchProcessCompleted(int count, Long batchStartTimestamp, String batchRequestId) {
        var executionTimeMs = new Date().getTime() - batchStartTimestamp;
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "batch process completed")
                .put(
                    "extra",
                    new JSONObject()
                        .put("executionTime", executionTimeMs)
                        .put("count", count)
                        .put("batchRequestId", batchRequestId)
                )
        );
        processBatchExecutionTime.observe((double) executionTimeMs / 1000);
    }

    public static void processMessageStarted(
        ConsumerRecord<String, String> record,
        String batchRequestId,
        String targetRequestId
    ) {
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
                                .put(
                                    "headers",
                                    Arrays
                                        .stream(record.headers().toArray())
                                        .map(header ->
                                            header.key() + "->" + new String(header.value(), StandardCharsets.UTF_8)
                                        )
                                        .collect(Collectors.joining(","))
                                )
                                .put("key", record.key())
                        )
                        .put("batchRequestId", batchRequestId)
                        .put("targetRequestId", targetRequestId)
                )
        );
    }

    public static void processMessageCompleted(
        ConsumerRecord<String, String> record,
        String batchRequestId,
        String targetRequestId,
        long executionStart,
        int statusCode,
        Throwable throwable
    ) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "process message completed")
                .put(
                    "extra",
                    new JSONObject()
                        .put("recordKey", record.key())
                        .put("statusCode", statusCode)
                        .put("exception", throwable)
                        .put("batchRequestId", batchRequestId)
                        .put("targetRequestId", targetRequestId)
                )
        );
        processMessageExecutionTime.observe(((double) (new Date().getTime() - executionStart)) / 1000);
        processMessageSuccess.inc();
    }

    public static void processMessageError(
        ConsumerRecord<String, String> record,
        Throwable exception,
        String batchRequestId,
        String targetRequestId
    ) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "process message failed")
                .put(
                    "err",
                    new JSONObject()
                        .put("errorMessages", getErrorMessages(exception))
                        .put("class", exception.getClass())
                        .put("stacktrace", exception.getStackTrace())
                )
                .put(
                    "extra",
                    new JSONObject()
                        .put("recordKey", record.key())
                        .put("batchRequestId", batchRequestId)
                        .put("targetRequestId", targetRequestId)
                )
        );

        processMessageError.inc();
    }

    public static void deadLetterProduced(
        ConsumerRecord<String, String> record,
        String batchRequestId,
        String targetRequestId
    ) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "dead letter produced")
                .put(
                    "extra",
                    new JSONObject()
                        .put("recordKey", record.key())
                        .put("originalTopic", record.topic())
                        .put("deadLetterTopic", Config.DEAD_LETTER_TOPIC)
                        .put("batchRequestId", batchRequestId)
                        .put("targetRequestId", targetRequestId)
                )
        );

        deadLetterProduced.inc();
    }

    public static void consumerError(Throwable exception) {
        write(
            new JSONObject()
                .put("level", "error")
                .put("message", "consumer stream was terminated due to unexpected error")
                .put(
                    "err",
                    new JSONObject()
                        .put("errorMessages", getErrorMessages(exception))
                        .put("class", exception.getClass())
                        .put("stacktrace", exception.getStackTrace())
                )
        );
    }

    public static void messageAcknowledge(
        ConsumerRecord<String, String> record,
        String batchRequestId,
        String targetRequestId
    ) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "message acknowledged")
                .put(
                    "extra",
                    new JSONObject()
                        .put("key", record.key())
                        .put("topic", record.topic())
                        .put("partition", record.partition())
                        .put("offset", record.offset())
                        .put("batchRequestId", batchRequestId)
                        .put("targetRequestId", targetRequestId)
                )
        );
    }

    public static void commitSuccess(String batchRequestId) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "commit success")
                .put("extra", new JSONObject().put("batchRequestId", batchRequestId))
        );
    }

    public static void commitFailed(Throwable exception, String batchRequestId) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "commit failed")
                .put("extra", new JSONObject().put("batchRequestId", batchRequestId))
                .put(
                    "err",
                    new JSONObject()
                        .put("errorMessages", getErrorMessages(exception))
                        .put("class", exception.getClass())
                        .put("stacktrace", exception.getStackTrace())
                )
        );
    }

    public static void consumerCompleted() {
        write(new JSONObject().put("level", "error").put("message", "consumer stream was completed"));
    }

    public static void shuttingDown() {
        write(new JSONObject().put("level", "info").put("message", "shutting down"));
    }

    public static void initializationError(Throwable exception) {
        write(
            new JSONObject()
                .put("level", "error")
                .put("message", "Unexpected error while initializing")
                .put(
                    "err",
                    new JSONObject()
                        .put("errorMessages", getErrorMessages(exception))
                        .put("class", exception.getClass())
                )
        );
    }

    public static void started() {
        write(new JSONObject().put("level", "info").put("message", "kafka-consumer-" + Config.GROUP_ID + " started"));
    }

    public static void assignedToPartition(Collection<ReceiverPartition> partitions) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "consumer was assigned to partitions")
                .put(
                    "extra",
                    new JSONObject()
                        .put("count", partitions.size())
                        .put(
                            "topicPartitions",
                            partitions.stream().map(x -> x.topicPartition().toString()).collect(Collectors.joining(","))
                        )
                )
        );
        assignedPartitions.inc(partitions.size());
    }

    public static void revokedFromPartition(Collection<ReceiverPartition> partitions) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "consumer was revoked from partitions")
                .put("extra", new JSONObject().put("count", partitions.size()))
        );

        assignedPartitions.dec(partitions.size());
    }

    public static void serviceTerminated() {
        write(
            new JSONObject().put("level", "info").put("message", "kafka-consumer-" + Config.GROUP_ID + " terminated")
        );
    }

    public static void produceError(String topic, String batchRequestId, String targetRequestId, Throwable exception) {
        write(
            new JSONObject()
                .put("level", "error")
                .put("message", String.format("failed producing message to %s topic", topic))
                .put(
                    "extra",
                    new JSONObject().put("batchRequestId", batchRequestId).put("targetRequestId", targetRequestId)
                )
                .put("err", new JSONObject().put("message", exception.getMessage()))
        );

        produceError.inc();
    }

    private static void logTargetRetry(
        Optional<String> responseBody,
        Throwable exception,
        String batchRequestId,
        String targetRequestId,
        String message
    ) {
        var extra = new JSONObject();
        extra.put("batchRequestId", batchRequestId);
        extra.put("targetRequestId", targetRequestId);
        if (responseBody.isPresent()) {
            extra.put("response", responseBody.get());
        }

        var error = new JSONObject();
        if (exception != null) {
            error.put("message", exception.getMessage());
            error.put("type", exception.getClass());
        }

        write(new JSONObject().put("level", "info").put("message", message).put("extra", extra).put("err", error));
    }

    public static void targetExecutionRetry(
        Optional<String> responseBody,
        Throwable exception,
        int attempt,
        String batchRequestId,
        String targetRequestId
    ) {
        logTargetRetry(
            responseBody,
            exception,
            batchRequestId,
            targetRequestId,
            String.format("target execution retry, attempt %s", attempt)
        );
        targetExecutionRetry.labels(String.valueOf(attempt)).inc();
    }

    public static void targetConnectionRetry(
        Optional<String> responseBody,
        Throwable exception,
        int attempt,
        String batchRequestId,
        String targetRequestId
    ) {
        logTargetRetry(
            responseBody,
            exception,
            batchRequestId,
            targetRequestId,
            String.format("target connection retry, attempt %s", attempt)
        );
        targetConnectionRetry.labels(String.valueOf(attempt)).inc();
    }

    public static void targetHealthcheckFailed(Exception exception) {
        write(
            new JSONObject()
                .put("level", "info")
                .put("message", "target healthcheck failed")
                .put("exceptionMessage", exception.getMessage())
        );
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
