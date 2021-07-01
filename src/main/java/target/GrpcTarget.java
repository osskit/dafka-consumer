package target;

import com.spotify.futures.ListenableFuturesExtra;
import configuration.Config;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import java.util.Date;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.function.CheckedSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class GrpcTarget implements ITarget {

  private Channel client;
  private TargetRetryPolicy retryPolicy;

  public GrpcTarget(final TargetRetryPolicy retryPolicy) {
    var target = Config.TARGET.split(":");
    this.client =
      ManagedChannelBuilder
        .forAddress(target[0], Integer.parseInt(target[1]))
        .usePlaintext()
        .build();
    this.retryPolicy = retryPolicy;
  }

  public CompletableFuture<TargetResponse> call(
    final ConsumerRecord<String, String> record
  ) {
    final var json = record.value();
    final var callTargetPayloadBuilder = Message.CallTargetPayload.newBuilder();
    callTargetPayloadBuilder.setRecordOffset(record.offset());
    callTargetPayloadBuilder.setRecordTimestamp(record.timestamp());
    callTargetPayloadBuilder.setTopic(this.getOriginalTopic(record));
    callTargetPayloadBuilder.setMsgJson(json);
    callTargetPayloadBuilder.setHeadersJson(this.getRecordHeaders(record));
    final CallTargetGrpc.CallTargetFutureStub futureStub = CallTargetGrpc.newFutureStub(
      client
    );

    final long startTime = (new Date()).getTime();
    CheckedSupplier<CompletionStage<Message.CallTargetResponse>> completionStageCheckedSupplier = () ->
      ListenableFuturesExtra.toCompletableFuture(
        futureStub.callTarget(callTargetPayloadBuilder.build())
      );

    return Failsafe
      .with(
        retryPolicy.<Message.CallTargetResponse>get(
          record,
          r -> r.getStatusCode()
        )
      )
      .getStageAsync(completionStageCheckedSupplier)
      .thenApplyAsync(
        response -> {
          var callLatency = response.getReceivedTimestamp() == 0L
            ? OptionalLong.empty()
            : OptionalLong.of(response.getReceivedTimestamp() - startTime);
          var resultLatency = response.getCompletedTimestamp() == 0L
            ? OptionalLong.empty()
            : OptionalLong.of(
              (new Date()).getTime() - response.getCompletedTimestamp()
            );
          return new TargetResponse(callLatency, resultLatency);
        }
      );
  }
}
