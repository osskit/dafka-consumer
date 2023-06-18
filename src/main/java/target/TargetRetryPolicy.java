package target;

import configuration.Config;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.function.ToIntFunction;
import kafka.Producer;
import monitoring.Monitor;
import net.jodah.failsafe.RetryPolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TargetRetryPolicy {

    private final Producer producer;
    private final String retryTopic;
    private final String deadLetterTopic;

    public TargetRetryPolicy(Producer producer, String retryTopic, String deadLetterTopic) {
        this.producer = producer;
        this.retryTopic = retryTopic;
        this.deadLetterTopic = deadLetterTopic;
    }

    public <T> RetryPolicy<T> get(ConsumerRecord<String, String> record, final ToIntFunction<T> getStatusCode) {
        var executionStart = new Date().getTime();
        var delay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(0);
        var maxDelay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(1);
        var delayFactor = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(2);
        var maxDuration = Duration.ofMillis(Config.RETRY_POLICY_MAX_DURATION_MS);

        return new RetryPolicy<T>()
            .withBackoff(delay, maxDelay, ChronoUnit.MILLIS, delayFactor)
            .withMaxDuration(maxDuration)
            .handleIf(e -> true)
            .handleResultIf(r ->
                String.valueOf(getStatusCode.applyAsInt(r)).matches(Config.RETRY_PROCESS_WHEN_STATUS_CODE_MATCH)
            )
            .onSuccess(x -> {
                var statusCode = String.valueOf(getStatusCode.applyAsInt(x.getResult()));

                if (statusCode.matches(Config.PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                    Monitor.processMessageError();
                    if (retryTopic != null) {
                        producer.produce(retryTopic, record);
                        Monitor.retryProduced(record);
                        return;
                    }
                }

                if (statusCode.matches(Config.PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH)) {
                    Monitor.processMessageError();
                    if (deadLetterTopic != null) {
                        producer.produce(deadLetterTopic, record);
                        Monitor.deadLetterProcdued(record);
                    }
                    return;
                }

                Monitor.processMessageSuccess(executionStart);
            })
            .onRetry(x -> {
                Optional<String> result = Optional.empty();

                if (x.getLastResult() != null) {
                    result = Optional.of(String.valueOf(getStatusCode.applyAsInt(x.getLastResult())));
                }

                Monitor.targetExecutionRetry(record, result, x.getLastFailure(), x.getAttemptCount());
            })
            .onRetriesExceeded(x -> {
                if (retryTopic != null) {
                    producer.produce(retryTopic, record);
                    Monitor.retryProduced(record);
                }
            });
    }
}
