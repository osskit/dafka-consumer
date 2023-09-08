package target;

import configuration.Config;
import dev.failsafe.RetryPolicy;
import dev.failsafe.event.ExecutionAttemptedEvent;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.okhttp.FailsafeCall;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import monitoring.Monitor;
import okhttp3.Response;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TargetRetryPolicy {

    public static FailsafeCall.FailsafeCallBuilder create(ConsumerRecord<String, String> record, String requestId) {
        var delay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(0);
        var maxDelay = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(1);
        var delayFactor = Config.RETRY_POLICY_EXPONENTIAL_BACKOFF.get(2);
        var maxDuration = Duration.ofMillis(Config.RETRY_POLICY_MAX_DURATION_MS);

        Monitor.processMessageStarted(record, requestId);

        var connectionFailureDelay = Config.CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF.get(0);
        var connectionFailureMaxDelay = Config.CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF.get(1);
        var connectionFailureDelayFactor = Config.CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF.get(2);
        var connectionFailureMaxDuration = Duration.ofMillis(Config.CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS);

        var connectionRetryPolicy = RetryPolicy
            .<Response>builder()
            .withBackoff(
                connectionFailureDelay,
                connectionFailureMaxDelay,
                ChronoUnit.MILLIS,
                connectionFailureDelayFactor
            )
            .withMaxRetries(Config.CONNECTION_FAILURE_RETRY_POLICY_MAX_RETRIES)
            //                .handleIf((r, e) -> {
            //                    System.out.println("111111 " +  e.getMessage());
            //                    return true;
            //                })
            .handle(IOException.class)
            //            .handleResultIf(r -> Integer.toString(r.code()).matches("503"))
            .withMaxDuration(connectionFailureMaxDuration)
            .onRetry(e -> {
                Monitor.targetConnectionRetry(
                    extractAttemptedResponseBody(e),
                    e.getLastException(),
                    e.getAttemptCount(),
                    requestId
                );
            })
            .onFailure(__ -> Monitor.targetConnectionRetryExceeded(requestId))
            .onSuccess(e ->
                Monitor.targetConnectionRetrySuccess(extractCompletedResponseBody(e), e.getAttemptCount(), requestId)
            )
            .build();

        var executionRetryPolicy = dev.failsafe.RetryPolicy
            .<Response>builder()
            .withBackoff(delay, maxDelay, ChronoUnit.MILLIS, delayFactor)
            .withMaxRetries(Config.RETRY_POLICY_MAX_RETRIES)
            .handleResultIf(r -> Integer.toString(r.code()).matches(Config.RETRY_PROCESS_WHEN_STATUS_CODE_MATCH))
            .withMaxDuration(maxDuration)
            .onRetry(e -> {
                Monitor.targetExecutionRetry(
                    extractAttemptedResponseBody(e),
                    e.getLastException(),
                    e.getAttemptCount(),
                    requestId
                );
            })
            .onSuccess(e ->
                Monitor.targetExecutionRetrySuccess(extractCompletedResponseBody(e), e.getAttemptCount(), requestId)
            )
            .build();

        return FailsafeCall.with(connectionRetryPolicy);
    }

    private static Optional<String> extractAttemptedResponseBody(ExecutionAttemptedEvent<Response> e) {
        return Optional
            .ofNullable(e.getLastResult())
            .flatMap(r -> {
                try {
                    try (Response response = r) {
                        return Optional.of(response.body().string());
                    }
                } catch (IOException error) {
                    return Optional.empty();
                }
            });
    }

    private static Optional<String> extractCompletedResponseBody(ExecutionCompletedEvent<Response> e) {
        return Optional
            .ofNullable(e.getResult())
            .flatMap(r -> {
                try {
                    try (Response response = r) {
                        return Optional.of(response.body().string());
                    }
                } catch (IOException error) {
                    return Optional.empty();
                }
            });
    }
}
