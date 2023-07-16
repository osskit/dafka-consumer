package kafka;

import configuration.Config;
import java.util.concurrent.CompletableFuture;
import monitoring.Monitor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

public class Producer {

    private final KafkaProducer<String, String> producer;

    public Producer(KafkaProducer<String, String> producer) {
        this.producer = producer;
    }

    private Headers getHeaders(ConsumerRecord<String, String> record) {
        Headers headers = record.headers();
        Header originalTopic = headers.lastHeader(Config.ORIGINAL_TOPIC);
        Headers headersToSend = new RecordHeaders();

        for (Header header : headers) {
            headersToSend.add(header);
        }

        if (originalTopic != null) {
            return headersToSend;
        }

        headersToSend.add(Config.ORIGINAL_TOPIC, record.topic().getBytes());
        headersToSend.add("x-group-id", Config.GROUP_ID.getBytes());
        headersToSend.add("x-original-key", record.key());

        return headersToSend;
    }

    // Source: https://github.com/1and1/reactive/blob/e582c0bdbfb4ab2a0780c77419d0d3ee67f08067/reactive-kafka/src/main/java/net/oneandone/reactive/kafka/CompletableKafkaProducer.java#L42
    public CompletableFuture<Void> produce(String topic, ConsumerRecord<String, String> record, String requestId) {
        Headers headers = getHeaders(record);

        final CompletableFuture<Void> promise = new CompletableFuture<>();

        final Callback callback = (metadata, exception) -> {
            if (exception == null) {
                promise.complete(null);
            } else {
                Monitor.produceError(topic, requestId, exception);
                promise.completeExceptionally(exception);
            }
        };

        producer.send(new ProducerRecord<>(topic, null, record.key(), record.value(), headers), callback);

        return promise;
    }
}
