package target;

import configuration.Config;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

public interface ITarget {
    CompletableFuture<TargetResponse> call(ConsumerRecord<String, String> record);

    default String getOriginalTopic(ConsumerRecord<String, String> record) {
        Iterator<Header> headers = record.headers().headers(Config.ORIGINAL_TOPIC).iterator();
        if (headers.hasNext()) {
            return new String(headers.next().value());
        }
        return record.topic();
    }

    default String getRecordHeader(ConsumerRecord<String, String> record, String key) {
        System.out.println("looking for header " + key);
        if (record.headers() != null) {
            Iterator<Header> headers = record.headers().iterator();
            while (headers.hasNext()) {
                Header header = headers.next();
                if (header.key().equals(key)) {
                    System.out.println("found " + key + " " + new String(header.value(), StandardCharsets.UTF_8));
                    return new String(header.value(), StandardCharsets.UTF_8);
                }
            }
        }

        return null;
    }
}
