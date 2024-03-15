package kafka;

import configuration.Config;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import monitoring.Monitor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import target.ITarget;
import target.TargetException;

public class Consumer {

    private final KafkaReceiver<String, String> kafkaReceiver;
    private final KafkaSender<String, String> kafkaSender;
    private final ITarget target;

    public Consumer(
        KafkaReceiver<String, String> kafkaReceiver,
        KafkaSender<String, String> kafkaSender,
        ITarget target
    ) {
        this.kafkaReceiver = kafkaReceiver;
        this.kafkaSender = kafkaSender;
        this.target = target;
    }

    private Flux<List<ReceiverRecord<String, String>>> processAsBatch(Flux<ReceiverRecord<String, String>> records) {
        return records
            .groupBy(ConsumerRecord::topic)
            .flatMap(Flux::collectList)
            .publishOn(Schedulers.parallel())
            .flatMap(receiverRecords -> {
                var batchRequestId = UUID.randomUUID().toString();
                var batchStartTimestamp = new Date().getTime();
                Monitor.batchProcessStarted(batchRequestId);
                var targetRequestId = UUID.randomUUID().toString();
                return Mono
                    .fromFuture(target.callBatch(receiverRecords, batchRequestId, targetRequestId))
                    .flatMap(targetResult -> {
                        if (targetResult instanceof TargetException && Config.DEAD_LETTER_TOPIC != null) {
                            return Flux
                                .fromIterable(receiverRecords)
                                .flatMap(record ->
                                    kafkaSender
                                        .send(
                                            Mono.just(
                                                SenderRecord.create(
                                                    new ProducerRecord<>(
                                                        Config.DEAD_LETTER_TOPIC,
                                                        null,
                                                        record.key(),
                                                        record.value(),
                                                        ((TargetException) targetResult).getHeaders(record)
                                                    ),
                                                    null
                                                )
                                            )
                                        )
                                        .doOnComplete(() ->
                                            Monitor.deadLetterProduced(record, batchRequestId, targetRequestId)
                                        )
                                )
                                .then(Mono.just(receiverRecords));
                        }
                        return Mono.just(receiverRecords);
                    })
                    .doOnSuccess(batch -> {
                        var lastRecord = receiverRecords.get(receiverRecords.size() - 1);
                        lastRecord.receiverOffset().acknowledge();
                        Monitor.messageAcknowledge(lastRecord, batchRequestId, targetRequestId);
                        Monitor.batchProcessCompleted(receiverRecords.size(), batchStartTimestamp, batchRequestId);
                    });
            });
    }

    private Mono<List<ReceiverRecord<String, String>>> processAsStream(Flux<ReceiverRecord<String, String>> records) {
        var batchRequestId = UUID.randomUUID().toString();
        var batchStartTimestamp = new Date().getTime();
        Monitor.batchProcessStarted(batchRequestId);
        return records
            .groupBy(record -> record.key() == null ? record.partition() : record.key())
            .delayElements(Duration.ofMillis(0))
            .publishOn(Schedulers.parallel())
            .flatMap(partition ->
                partition.concatMap(record -> {
                    var targetRequestId = UUID.randomUUID().toString();
                    return Mono
                        .fromFuture(target.call(record, batchRequestId, targetRequestId))
                        .then(Mono.just(record))
                        .doOnSuccess(r -> {
                            r.receiverOffset().acknowledge();
                            Monitor.messageAcknowledge(r, batchRequestId, targetRequestId);
                        });
                })
            )
            .collectList()
            .doOnNext(batch -> Monitor.batchProcessCompleted(batch.size(), batchStartTimestamp, batchRequestId));
    }

    public Flux<?> stream() {
        return kafkaReceiver
            .receiveBatch()
            .concatMap(records ->
                Config.TARGET_PROCESS_TYPE.equals("batch") ? processAsBatch(records) : processAsStream(records)
            );
    }
}
