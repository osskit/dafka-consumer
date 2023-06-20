package monitoring;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

interface LoggerLambda {
    void log();
}

public class Monitor {

    private final String scope;
    private static final Logger logger = LoggerFactory.getLogger(Monitor.class);
    private final Counter counter;
    private final Histogram histogram;

    public Monitor(String scope, Collection<String> labelNames) {
        this.scope = scope;

        List<String> allLabels = new ArrayList<>();
        allLabels.add("method");
        allLabels.add("result");
        allLabels.addAll(labelNames);

        String metricName = scope.replaceAll("-", "_");

        this.counter =
            Counter
                .build()
                .name(String.format("%s_success", metricName))
                .labelNames(allLabels.toArray(new String[0]))
                .help(String.format("%s_success", metricName))
                .register();

        this.histogram =
            Histogram
                .build()
                .name(String.format("%s_execution_time", metricName))
                .labelNames(allLabels.toArray(new String[0]))
                .help(String.format("%s_execution_time", metricName))
                .register();
    }

    private void logWithContext(LoggerLambda log, Map<String, String> context) {
        context.forEach((key, value) -> MDC.put(key, value));
        log.log();
        context.clear();
    }

    public <T> CompletableFuture<T> monitor(
        String method,
        Collection<String> labelValues,
        Map<String, String> context,
        CompletableFuture<T> future
    ) {
        List<String> actualLabelValues = new ArrayList<>();
        actualLabelValues.add(method);
        actualLabelValues.add("success");
        actualLabelValues.addAll(labelValues);

        Histogram.Timer timer = histogram.labels(actualLabelValues.toArray(new String[0])).startTimer();

        logWithContext(() -> logger.info(String.format("%s.%s started", scope, method)), context);

        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                actualLabelValues.clear();
                actualLabelValues.add(method);
                actualLabelValues.add("error");
                actualLabelValues.addAll(labelValues);

                counter.labels(actualLabelValues.toArray(new String[0])).inc();
                logWithContext(() -> logger.error(String.format("%s.%s error", scope, method), throwable), context);
            } else {
                counter.labels(actualLabelValues.toArray(new String[0])).inc();
                histogram.labels(actualLabelValues.toArray(new String[0])).observe(timer.observeDuration());
                logWithContext(() -> logger.info(String.format("%s.%s success", scope, method)), context);
            }
        });

        return future;
    }
}
