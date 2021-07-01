package target;

import configuration.Config;

public class TargetFactory {

    public static ITarget create(TargetRetryPolicy targetRetryPolicy) {
        return Config.SENDING_PROTOCOL.equals("grpc")
            ? new GrpcTarget(targetRetryPolicy)
            : new HttpTarget(targetRetryPolicy);
    }
}
