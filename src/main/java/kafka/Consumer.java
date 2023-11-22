package kafka;

import configuration.Config;
import java.time.Duration;
import java.util.Date;
import monitoring.Monitor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import target.ITarget;

public class Consumer {

    private final KafkaReceiver<String, String> kafkaReceiver;
    private final ITarget target;

    public Consumer(KafkaReceiver<String, String> kafkaReceiver, ITarget target) {
        this.kafkaReceiver = kafkaReceiver;
        this.target = target;
    }

    public Flux<?> stream() {
        return kafkaReceiver
            .receiveBatch()
            .doOnNext(records -> records.count().doOnNext(Monitor::batchProcessStarted))
            .concatMap(records -> {
                var batchStartTimestamp = new Date().getTime();
                return records
                    .groupBy(ConsumerRecord::key)
                    .delayElements(Duration.ofMillis(Config.PROCESSING_DELAY))
                    .publishOn(Schedulers.parallel())
                    .flatMap(partition ->
                        partition.concatMap(record -> Mono.fromFuture(target.call(record)).map(__ -> record))
                    )
                    .collectList()
                    .doOnNext(__ -> Monitor.batchProcessCompleted(batchStartTimestamp));
            })
            .flatMap(records ->
                Mono.fromRunnable(() -> {
                    var lastOffset = records.get(records.size() - 1).receiverOffset();
                    lastOffset.commit().block();
                })
            );
    }
}
