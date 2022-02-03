package target;

import configuration.Config;
import configuration.TopicsRoutes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Date;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import monitoring.Monitor;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.function.CheckedSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class HttpTarget implements ITarget {

    private final HttpClient client = HttpClient.newHttpClient();
    private TargetRetryPolicy retryPolicy;
    private TopicsRoutes topicsRoutes;

    public HttpTarget(final TargetRetryPolicy retryPolicy, TopicsRoutes topicsRoutes) {
        this.retryPolicy = retryPolicy;
        this.topicsRoutes = topicsRoutes;
    }

    public CompletableFuture<TargetResponse> call(final ConsumerRecord<String, String> record) {
        if (this.getRecordCorrelationId(record) == null) {
            Monitor.missingCorrelationId(record);

            return new CompletableFuture<>();
        }

        final var request = HttpRequest
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .uri(URI.create(Config.TARGET_BASE_URL + this.topicsRoutes.getRoute(record.topic())))
            .header("Content-Type", "application/json")
            .header("x-record-topic", record.topic())
            .header("x-record-partition", String.valueOf(record.partition()))
            .header("x-record-offset", String.valueOf(record.offset()))
            .header("x-record-timestamp", String.valueOf(record.timestamp()))
            .header("x-record-original-topic", this.getOriginalTopic(record))
            .header(Config.CORRELATION_ID_HEADER, this.getRecordCorrelationId(record))
            .header("x-record-headers", this.getRecordHeaders(record))
            .POST(HttpRequest.BodyPublishers.ofString(record.value()))
            .timeout(Duration.ofMillis(Config.TARGET_TIMEOUT_MS))
            .build();

        final long startTime = (new Date()).getTime();
        final CheckedSupplier<CompletionStage<HttpResponse<String>>> completionStageCheckedSupplier = () ->
            client
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete(
                    (__, throwable) -> {
                        if (throwable instanceof HttpTimeoutException) {
                            Monitor.targetExecutionTimeout(record);
                        }
                    }
                );
        return Failsafe
            .with(retryPolicy.<HttpResponse<String>>get(record, r -> r.statusCode()))
            .getStageAsync(completionStageCheckedSupplier)
            .thenApplyAsync(
                response -> {
                    var callLatency = !response.headers().firstValueAsLong("x-received-timestamp").isPresent()
                        ? OptionalLong.empty()
                        : OptionalLong.of(
                            response.headers().firstValueAsLong("x-received-timestamp").getAsLong() - startTime
                        );
                    var resultLatency = !response.headers().firstValueAsLong("x-completed-timestamp").isPresent()
                        ? OptionalLong.empty()
                        : OptionalLong.of(
                            (new Date()).getTime() -
                            response.headers().firstValueAsLong("x-completed-timestamp").getAsLong()
                        );
                    return new TargetResponse(callLatency, resultLatency);
                }
            );
    }
}
