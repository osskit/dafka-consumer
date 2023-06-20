package kafka;

import configuration.Config;
import java.time.Duration;
import java.util.Date;
import monitoring.LegacyMonitor;
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
            .doOnNext(records -> LegacyMonitor.batchProcessStarted(records.count()))
            .concatMap(records -> {
                var batchStartTimestamp = new Date().getTime();
                return Flux
                    .fromIterable(records)
                    .groupBy(ConsumerRecord::partition)
                    .delayElements(Duration.ofMillis(Config.PROCESSING_DELAY))
                    .publishOn(Schedulers.parallel())
                    .flatMap(partition ->
                        partition
                            .doOnNext(LegacyMonitor::processMessageStarted)
                            .concatMap(record -> Mono.fromFuture(target.call(record)))
                    )
                    .collectList()
                    .map(__ -> batchStartTimestamp);
            })
            .doOnNext(LegacyMonitor::batchProcessCompleted)
            .map(__ -> {
                kafkaConsumer.commit();
                return 0;
            })
            .doOnNext(__ -> kafkaConsumer.poll());
    }
}
