package kafka;

import configuration.Config;
import java.time.Duration;
import java.util.Date;
import java.util.stream.StreamSupport;
import monitoring.Monitor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import target.ITarget;
import reactor.kafka.receiver.KafkaReceiver;

public class Consumer {
    private final KafkaReceiver<?, ?> foo;
    private final ReactiveKafkaClient<String, String> kafkaConsumer;
    private final ITarget target;

    public Consumer(ReactiveKafkaClient<String, String> kafkaConsumer, ITarget target) {
        this.kafkaConsumer = kafkaConsumer;
        this.target = target;
    }

    public Flux<?> stream() {
        return kafkaConsumer
            .doOnNext(records -> Monitor.batchProcessStarted(records.count()))
            .concatMap(records -> {
                var batchStartTimestamp = new Date().getTime();
                var hasNullKey = StreamSupport
                    .stream(records.spliterator(), false)
                    .anyMatch(record -> record.key() == null);

                return Flux
                    .fromIterable(records)
                    .groupBy(record -> hasNullKey ? record.partition() : record.key())
                    .delayElements(Duration.ofMillis(Config.PROCESSING_DELAY))
                    .publishOn(Schedulers.parallel())
                    .flatMap(partition -> partition.concatMap(record -> Mono.fromFuture(target.call(record))))
                    .collectList()
                    .map(__ -> batchStartTimestamp);
            })
            .doOnNext(Monitor::batchProcessCompleted)
            .map(__ -> {
                kafkaConsumer.commit();
                return 0;
            })
            .doOnNext(__ -> kafkaConsumer.poll());
    }
}
