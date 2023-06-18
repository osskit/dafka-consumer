package kafka;

import configuration.Config;
import java.util.concurrent.Future;
import monitoring.Monitor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
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

        return headersToSend;
    }

    public Future<RecordMetadata> produce(String topic, ConsumerRecord<String, String> record) {
        Headers headers = getHeaders(record);

        return producer.send(new ProducerRecord<>(topic, null, record.key(), record.value(), headers));
    }
}
