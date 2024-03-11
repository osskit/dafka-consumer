package kafka;

import configuration.Config;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.network.Send;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.SenderOptions;

public class KafkaClientFactory {

    private static Properties getAuthProperties() {
        var props = new Properties();
        props.put("bootstrap.servers", Config.KAFKA_BROKER);

        if (!Config.USE_SASL_AUTH) {
            return props;
        }

        props.put("security.protocol", "SASL_SSL");

        if (Config.TRUSTSTORE_PASSWORD != null) {
            props.put("ssl.truststore.location", Config.TRUSTSTORE_FILE_PATH);
            props.put("ssl.truststore.password", Config.TRUSTSTORE_PASSWORD);
        }

        props.put("sasl.mechanism", Config.SASL_MECHANISM.toUpperCase());
        props.put("ssl.endpoint.identification.algorithm", "");
        props.put(
            "sasl.jaas.config",
            String.format(
                "org.apache.kafka.common.security.%s required username=\"%s\" password=\"%s\";",
                getSaslMechanism(),
                Config.SASL_USERNAME,
                Config.SASL_PASSWORD
            )
        );

        return props;
    }

    private static String getSaslMechanism() {
        return switch (Config.SASL_MECHANISM.toUpperCase()) {
            case "PLAIN" -> "plain.PlainLoginModule";
            case "SCRAM-SHA-512" -> "scram.ScramLoginModule";
            default -> "";
        };
    }

    public static ReceiverOptions<String, String> createReceiverOptions() {
        var props = getAuthProperties();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, Config.GROUP_ID);
        props.put(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.StringDeserializer"
        );
        props.put(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            "org.apache.kafka.common.serialization.StringDeserializer"
        );
        if (Config.ASSIGNMENT_STRATEGY != null && Config.ASSIGNMENT_STRATEGY.size() > 0) {
            props.put(
                ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                String.join(",", Config.ASSIGNMENT_STRATEGY)
            );
        }
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Config.MAX_POLL_RECORDS);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, Config.KAFKA_POLL_INTERVAL_MS);
        return ReceiverOptions.create(props);
    }

    public static SenderOptions<String, String> createSenderOptions() {
        var props = getAuthProperties();
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return SenderOptions.create(props);
    }

    public static <K, V> KafkaProducer<K, V> createProducer() {
        var props = getAuthProperties();
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return new KafkaProducer<>(props);
    }
}
