package kafka;

import configuration.Config;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import monitoring.Monitor;
import okhttp3.*;
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

    private Headers getHeaders(
        ConsumerRecord<String, String> record,
        Optional<Response> response,
        Optional<Throwable> throwable
    ) {
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
        response.ifPresent(value -> {
            headersToSend.add("x-response-status-code", String.valueOf(response.get().code()).getBytes());
            try {
                headersToSend.add("x-response-body", response.get().body().string().getBytes());
            } catch (Exception e) {
                //ignore
            }
        });
        throwable.ifPresent(value -> headersToSend.add("x-unexpected-exception-message", value.getMessage().getBytes())
        );

        return headersToSend;
    }

    // Source: https://github.com/1and1/reactive/blob/e582c0bdbfb4ab2a0780c77419d0d3ee67f08067/reactive-kafka/src/main/java/net/oneandone/reactive/kafka/CompletableKafkaProducer.java#L42
    public CompletableFuture<Object> produce(
        String topic,
        ConsumerRecord<String, String> record,
        Optional<Response> response,
        Optional<Throwable> throwable,
        String requestId
    ) {
        Headers headers = getHeaders(record, response, throwable);

        final CompletableFuture<Object> promise = new CompletableFuture<>();
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
