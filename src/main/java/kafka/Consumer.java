package kafka;

import configuration.Config;
import java.time.Duration;
import java.util.Date;
import monitoring.Monitor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import target.ITarget;

public class Consumer {

    private final ReactiveKafkaClient<String, String> kafkaConsumer;
    private final ITarget target;

    public Consumer(ReactiveKafkaClient<String, String> kafkaConsumer, ITarget target) {
        this.kafkaConsumer = kafkaConsumer;
        this.target = target;
    }

    public Flux<?> stream() {
        return kafkaConsumer
            .doOnNext(records -> Monitor.batchProcessStarted(records.count()))
            .concatMap(
                records -> {
                    var batchStartTimestamp = new Date().getTime();
                    return Flux
                        .fromIterable(records)
                        .groupBy(ConsumerRecord::partition)
                        .delayElements(Duration.ofMillis(Config.PROCESSING_DELAY))
                        .publishOn(Schedulers.parallel())
                        .flatMap(
                            partition ->
                                partition
                                    .doOnNext(Monitor::processMessageStarted)
                                    .filter(__ -> Config.DRAIN)
                                    .concatMap(
                                        record ->
                                            Mono
                                                .fromFuture(target.call(record))
                                                .doOnSuccess(
                                                    targetResponse -> {
                                                        if (targetResponse.callLatency.isPresent()) {
                                                            Monitor.callTargetLatency(
                                                                targetResponse.callLatency.getAsLong()
                                                            );
                                                        }
                                                        if (targetResponse.resultLatency.isPresent()) {
                                                            Monitor.resultTargetLatency(
                                                                targetResponse.resultLatency.getAsLong()
                                                            );
                                                        }
                                                    }
                                                )
                                    )
                        )
                        .collectList()
                        .map(__ -> batchStartTimestamp);
                }
            )
            .doOnNext(Monitor::batchProcessCompleted)
            .map(
                __ -> {
                    kafkaConsumer.commit();
                    return 0;
                }
            )
            .doOnNext(__ -> kafkaConsumer.poll());
    }
}
