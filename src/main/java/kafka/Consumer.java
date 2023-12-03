package kafka;

import configuration.Config;
import java.time.Duration;
import java.time.Month;
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
                var requestId = UUID.randomUUID().toString();
                var batchStartTimestamp = new Date().getTime();
                Monitor.batchProcessStarted(requestId);
                return records
                    .groupBy(x -> x.key() == null ? x.partition() : x.key())
                    .delayElements(Duration.ofMillis(Config.PROCESSING_DELAY))
                    .publishOn(Schedulers.parallel())
                    .flatMap(partition ->
                        partition.concatMap(record -> Mono.fromFuture(target.call(record, requestId)).thenReturn(record)
                        )
                    )
                    .doOnNext(record -> {
                        record.receiverOffset().acknowledge();
                        Monitor.messageAcknowledge(record, requestId);
                    })
                    .collectList()
                    .doOnNext(batch -> Monitor.batchProcessCompleted(batch.size(), batchStartTimestamp, requestId))
                    .flatMap(__ ->
                        records
                            .last()
                            .flatMap(record ->
                                record
                                    .receiverOffset()
                                    .commit()
                                    .doOnSuccess(___ -> Monitor.commitSuccess(requestId))
                                    .doOnError(throwable -> Monitor.commitFailed(throwable, requestId))
                            )
                    );
            });
    }
}
