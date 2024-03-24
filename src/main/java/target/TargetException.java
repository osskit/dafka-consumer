package target;

import configuration.Config;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import reactor.kafka.receiver.ReceiverRecord;

public class TargetException extends Exception {

    private final Integer responseCode;
    private final String responseBody;
    private final Throwable error;

    public TargetException(Integer responseCode, String responseBody, Throwable error) {
        this.responseCode = responseCode;
        this.responseBody = responseBody;
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
        headersToSend.add("x-response-status-code", String.valueOf(responseCode).getBytes());
        headersToSend.add("x-response-body", responseBody.getBytes());
        if (error != null) {
            headersToSend.add("x-unexpected-exception-message", error.getMessage().getBytes());
        }

        return headersToSend;
    }
}
