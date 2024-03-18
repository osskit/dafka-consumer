package kafka;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import configuration.Config;
import configuration.TopicsRoutes;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

    private final TopicsRoutes topicsRoutes;

    public Consumer(
        KafkaReceiver<String, String> kafkaReceiver,
        KafkaSender<String, String> kafkaSender,
        TopicsRoutes topicsRoutes,
        ITarget target
    ) {
        this.kafkaReceiver = kafkaReceiver;
        this.kafkaSender = kafkaSender;
        this.target = target;
        this.topicsRoutes = topicsRoutes;
    }

    private static Predicate<ReceiverRecord<String, String>> byRecordField() {
        return record -> {
            if (Config.RECORD_FILTER_FIELD.isEmpty()) {
                return true;
            }
            return new Gson()
                .fromJson(record.value(), JsonElement.class)
                .getAsJsonObject()
                .get(Config.RECORD_FILTER_FIELD)
                .getAsString()
                .equals(Config.RECORD_FILTER_VALUE);
        };
    }

    private Flux<List<ReceiverRecord<String, String>>> processAsBatch(Flux<ReceiverRecord<String, String>> records) {
        return records
            .filter(byRecordField())
            .groupBy(ConsumerRecord::topic)
            .flatMap(Flux::collectList)
            .publishOn(Schedulers.parallel())
            .flatMap(receiverRecords -> {
                var batchRequestId = UUID.randomUUID().toString();
                var batchStartTimestamp = new Date().getTime();
                Monitor.batchProcessStarted(batchRequestId);
                var targetRequestId = UUID.randomUUID().toString();
                return Mono
                    .fromFuture(
                        target.callBatch(
                            receiverRecords
                                .stream()
                                .map(r ->
                                    Config.RECORD_PROJECT_FIELD.isEmpty()
                                        ? r
                                        : new Gson()
                                            .fromJson(r.value(), JsonElement.class)
                                            .getAsJsonObject()
                                            .get(Config.RECORD_PROJECT_FIELD)
                                )
                                .collect(Collectors.toList()),
                            topicsRoutes.getRoute(receiverRecords.get(receiverRecords.size() - 1).topic()),
                            batchRequestId,
                            targetRequestId
                        )
                    )
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
                    .doOnSuccess(__ -> {
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
            .filter(byRecordField())
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
