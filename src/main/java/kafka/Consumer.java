package kafka;

import configuration.Config;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import monitoring.Monitor;
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
            .concatMap(records -> {
                var batchRequestId = UUID.randomUUID().toString();
                var batchStartTimestamp = new Date().getTime();
                Monitor.batchProcessStarted(batchRequestId);
                return records
                    .groupBy(x -> x.key() == null ? x.partition() : x.key())
                    .publishOn(Schedulers.parallel())
                    .flatMap(partition ->
                        partition.concatMap(record -> {
                            var targetRequestId = UUID.randomUUID().toString();
                            return Mono
                                .fromFuture(target.call(record, batchRequestId, targetRequestId))
                                .doOnSuccess(__ -> {
                                    record.receiverOffset().acknowledge();
                                    Monitor.messageAcknowledge(record, batchRequestId, targetRequestId);
                                });
                        })
                    )
                    .collectList()
                    .doOnNext(batch -> Monitor.batchProcessCompleted(batch.size(), batchStartTimestamp, batchRequestId)
                    );
            });
    }
}
