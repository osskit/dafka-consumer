package target;

import java.util.OptionalLong;

public class TargetResponse {

    public OptionalLong callLatency;
    public OptionalLong resultLatency;

    public TargetResponse() {}

    TargetResponse(OptionalLong callLatency, OptionalLong resultLatency) {
        this.callLatency = callLatency;
        this.resultLatency = resultLatency;
    }
}
