package target;

import configuration.Config;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.json.JSONObject;

public interface ITarget {
    CompletableFuture<TargetResponse> call(ConsumerRecord<String, String> record);

    default String getOriginalTopic(ConsumerRecord<String, String> record) {
        Iterator<Header> headers = record.headers().headers(Config.ORIGINAL_TOPIC).iterator();
        if (headers.hasNext()) {
            return new String(headers.next().value());
        }
        return record.topic();
    }

    default String getRecordHeaders(ConsumerRecord<String, String> record) {
        JSONObject headersJson = new JSONObject();
        if (record.headers() != null) {
            Iterator<Header> headers = record.headers().iterator();
            while (headers.hasNext()) {
                Header header = headers.next();
                headersJson.put(header.key(), new String(header.value()));
            }
        }
        return headersJson.toString();
    }
}
