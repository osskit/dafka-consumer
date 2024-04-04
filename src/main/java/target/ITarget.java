package target;

import configuration.Config;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.header.Header;
import reactor.kafka.receiver.ReceiverRecord;

public interface ITarget {
    CompletableFuture<Object> call(
        ReceiverRecord<String, String> record,
        String batchRequestId,
        String targetRequestId
    );

    CompletableFuture<Object> call(
        List<ReceiverRecord<String, String>> records,
        String batchRequestId,
        String targetRequestId
    );

    default String getOriginalTopic(ReceiverRecord<String, String> record) {
        Iterator<Header> headers = record.headers().headers(Config.ORIGINAL_TOPIC).iterator();
        if (headers.hasNext()) {
            return new String(headers.next().value());
        }
        return record.topic();
    }
}
