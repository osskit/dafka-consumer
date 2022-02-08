package target;

import configuration.Config;
import java.net.http.HttpRequest.Builder;
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

    default void copyRecordHeaders(Builder httpBuilder, ConsumerRecord<String, String> record) {
        if (record.headers() != null) {
            Iterator<Header> headers = record.headers().iterator();
            while (headers.hasNext()) {
                Header header = headers.next();
                httpBuilder.setHeader(header.key(), header.value().toString());
            }
        }
    }

    default String getRecordCorrelationId(ConsumerRecord<String, String> record) {
        if (record.headers() != null) {
            Iterator<Header> headers = record.headers().iterator();
            while (headers.hasNext()) {
                Header header = headers.next();
                if (header.key().equals(Config.CORRELATION_ID_HEADER_KEY)) {
                    return new String(header.value());
                }
            }
        }

        return null;
    }
}
