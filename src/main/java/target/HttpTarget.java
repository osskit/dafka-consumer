package target;

import configuration.Config;
import configuration.TopicsRoutes;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import monitoring.Monitor;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.function.CheckedSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class HttpTarget implements ITarget {

    private final TargetRetryPolicy retryPolicy;
    private final TopicsRoutes topicsRoutes;

    public HttpTarget(final TargetRetryPolicy retryPolicy, TopicsRoutes topicsRoutes) {
        this.retryPolicy = retryPolicy;
        this.topicsRoutes = topicsRoutes;
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
        final var builder = HttpRequest
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .uri(URI.create(Config.TARGET_BASE_URL + this.topicsRoutes.getRoute(record.topic())))
            .header("Content-Type", "application/json")
            .header("x-record-topic", record.topic())
            .header("x-record-partition", String.valueOf(record.partition()))
            .header("x-record-offset", String.valueOf(record.offset()))
            .header("x-record-timestamp", String.valueOf(record.timestamp()))
            .header("x-record-original-topic", this.getOriginalTopic(record))
            .POST(HttpRequest.BodyPublishers.ofString(record.value()))
            .timeout(Duration.ofMillis(Config.TARGET_TIMEOUT_MS));

        record
            .headers()
            .forEach(
                header -> {
                    String headerKey = header.key();

                    if (cloudEventHeaders.contains(headerKey)) {
                        headerKey = headerKey.replace("_", "-");
                    }

                    builder.header(headerKey, new String(header.value(), StandardCharsets.UTF_8));
                }
            );

        final var request = builder.build();
        final long startTime = (new Date()).getTime();

        var httpFuture = HttpClient
            .newHttpClient()
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .whenComplete(
                (__, throwable) -> {
                    if (throwable instanceof HttpTimeoutException) {
                        Monitor.targetExecutionTimeout(record);
                    }
                }
            );

        var result = new CompletableFuture<Optional<HttpResponse<String>>>();

        httpFuture.whenComplete(
            (ok, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(Optional.of(ok));
                }
            }
        );

        final CheckedSupplier<CompletionStage<Optional<HttpResponse<String>>>> completionStageCheckedSupplier = () ->
            result;

        Optional<HttpResponse<String>> defaultResponse = Optional.empty();

        return Failsafe
            .with(
                Fallback.of(defaultResponse),
                retryPolicy.get(record, o -> o.map(r -> r.statusCode()).orElseGet(() -> -1))
            )
            .getStageAsync(completionStageCheckedSupplier)
            .thenApplyAsync(
                optionalResponse -> {
                    if (optionalResponse.isEmpty()) {
                        return new TargetResponse(OptionalLong.empty(), OptionalLong.empty());
                    }

                    var response = optionalResponse.get();

                    var callLatency = response.headers().firstValueAsLong("x-received-timestamp").isEmpty()
                        ? OptionalLong.empty()
                        : OptionalLong.of(
                            response.headers().firstValueAsLong("x-received-timestamp").getAsLong() - startTime
                        );
                    var resultLatency = response.headers().firstValueAsLong("x-completed-timestamp").isEmpty()
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
