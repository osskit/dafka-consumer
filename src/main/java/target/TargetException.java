package target;

import configuration.Config;
import okhttp3.Response;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import reactor.kafka.receiver.ReceiverRecord;

public class TargetException extends Exception {

    private final Response response;
    private final Throwable error;

    public TargetException(Response response, Throwable error) {
        this.response = response;
        this.error = error;
    }

    public Headers getHeaders(ReceiverRecord<String, String> record) {
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
        if (response != null) {
            headersToSend.add("x-response-status-code", String.valueOf(response.code()).getBytes());
            try {
                assert response.body() != null;
                headersToSend.add("x-response-body", response.body().string().getBytes());
            } catch (Exception e) {
                //ignore
            }
        }
        if (error != null) {
            headersToSend.add("x-unexpected-exception-message", error.getMessage().getBytes());
        }

        return headersToSend;
    }
}
