package target;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.24.0)", comments = "Source: message.proto")
public final class CallTargetGrpc {

    private CallTargetGrpc() {}

    public static final String SERVICE_NAME = "CallTarget";

    // Static method descriptors that strictly reflect the proto.
    private static volatile io.grpc.MethodDescriptor<Message.CallTargetPayload, Message.CallTargetResponse> getCallTargetMethod;

    @io.grpc.stub.annotations.RpcMethod(
        fullMethodName = SERVICE_NAME + '/' + "callTarget",
        requestType = Message.CallTargetPayload.class,
        responseType = Message.CallTargetResponse.class,
        methodType = io.grpc.MethodDescriptor.MethodType.UNARY
    )
    public static io.grpc.MethodDescriptor<Message.CallTargetPayload, Message.CallTargetResponse> getCallTargetMethod() {
        io.grpc.MethodDescriptor<Message.CallTargetPayload, Message.CallTargetResponse> getCallTargetMethod;
        if ((getCallTargetMethod = CallTargetGrpc.getCallTargetMethod) == null) {
            synchronized (CallTargetGrpc.class) {
                if ((getCallTargetMethod = CallTargetGrpc.getCallTargetMethod) == null) {
                    CallTargetGrpc.getCallTargetMethod =
                        getCallTargetMethod =
                            io.grpc.MethodDescriptor
                                .<Message.CallTargetPayload, Message.CallTargetResponse>newBuilder()
                                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                                .setFullMethodName(generateFullMethodName(SERVICE_NAME, "callTarget"))
                                .setSampledToLocalTracing(true)
                                .setRequestMarshaller(
                                    io.grpc.protobuf.ProtoUtils.marshaller(
                                        Message.CallTargetPayload.getDefaultInstance()
                                    )
                                )
                                .setResponseMarshaller(
                                    io.grpc.protobuf.ProtoUtils.marshaller(
                                        Message.CallTargetResponse.getDefaultInstance()
                                    )
                                )
                                .setSchemaDescriptor(new CallTargetMethodDescriptorSupplier("callTarget"))
                                .build();
                }
            }
        }
        return getCallTargetMethod;
    }

    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static CallTargetStub newStub(io.grpc.Channel channel) {
        return new CallTargetStub(channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static CallTargetBlockingStub newBlockingStub(io.grpc.Channel channel) {
        return new CallTargetBlockingStub(channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static CallTargetFutureStub newFutureStub(io.grpc.Channel channel) {
        return new CallTargetFutureStub(channel);
    }

    /**
     */
    public abstract static class CallTargetImplBase implements io.grpc.BindableService {

        /**
         */
        public void callTarget(
            Message.CallTargetPayload request,
            io.grpc.stub.StreamObserver<Message.CallTargetResponse> responseObserver
        ) {
            asyncUnimplementedUnaryCall(getCallTargetMethod(), responseObserver);
        }

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return io.grpc.ServerServiceDefinition
                .builder(getServiceDescriptor())
                .addMethod(
                    getCallTargetMethod(),
                    asyncUnaryCall(
                        new MethodHandlers<Message.CallTargetPayload, Message.CallTargetResponse>(
                            this,
                            METHODID_CALL_TARGET
                        )
                    )
                )
                .build();
        }
    }

    /**
     */
    public static final class CallTargetStub extends io.grpc.stub.AbstractStub<CallTargetStub> {

        private CallTargetStub(io.grpc.Channel channel) {
            super(channel);
        }

        private CallTargetStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CallTargetStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CallTargetStub(channel, callOptions);
        }

        /**
         */
        public void callTarget(
            Message.CallTargetPayload request,
            io.grpc.stub.StreamObserver<Message.CallTargetResponse> responseObserver
        ) {
            asyncUnaryCall(getChannel().newCall(getCallTargetMethod(), getCallOptions()), request, responseObserver);
        }
    }

    /**
     */
    public static final class CallTargetBlockingStub extends io.grpc.stub.AbstractStub<CallTargetBlockingStub> {

        private CallTargetBlockingStub(io.grpc.Channel channel) {
            super(channel);
        }

        private CallTargetBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CallTargetBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CallTargetBlockingStub(channel, callOptions);
        }

        /**
         */
        public Message.CallTargetResponse callTarget(Message.CallTargetPayload request) {
            return blockingUnaryCall(getChannel(), getCallTargetMethod(), getCallOptions(), request);
        }
    }

    /**
     */
    public static final class CallTargetFutureStub extends io.grpc.stub.AbstractStub<CallTargetFutureStub> {

        private CallTargetFutureStub(io.grpc.Channel channel) {
            super(channel);
        }

        private CallTargetFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CallTargetFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CallTargetFutureStub(channel, callOptions);
        }

        /**
         */
        public com.google.common.util.concurrent.ListenableFuture<Message.CallTargetResponse> callTarget(
            Message.CallTargetPayload request
        ) {
            return futureUnaryCall(getChannel().newCall(getCallTargetMethod(), getCallOptions()), request);
        }
    }

    private static final int METHODID_CALL_TARGET = 0;

    private static final class MethodHandlers<Req, Resp>
        implements
            io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
            io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
            io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
            io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {

        private final CallTargetImplBase serviceImpl;
        private final int methodId;

        MethodHandlers(CallTargetImplBase serviceImpl, int methodId) {
            this.serviceImpl = serviceImpl;
            this.methodId = methodId;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                case METHODID_CALL_TARGET:
                    serviceImpl.callTarget(
                        (Message.CallTargetPayload) request,
                        (io.grpc.stub.StreamObserver<Message.CallTargetResponse>) responseObserver
                    );
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public io.grpc.stub.StreamObserver<Req> invoke(io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                default:
                    throw new AssertionError();
            }
        }
    }

    private abstract static class CallTargetBaseDescriptorSupplier
        implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        CallTargetBaseDescriptorSupplier() {}

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return Message.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("CallTarget");
        }
    }

    private static final class CallTargetFileDescriptorSupplier extends CallTargetBaseDescriptorSupplier {

        CallTargetFileDescriptorSupplier() {}
    }

    private static final class CallTargetMethodDescriptorSupplier
        extends CallTargetBaseDescriptorSupplier
        implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final String methodName;

        CallTargetMethodDescriptorSupplier(String methodName) {
            this.methodName = methodName;
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
            return getServiceDescriptor().findMethodByName(methodName);
        }
    }

    private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

    public static io.grpc.ServiceDescriptor getServiceDescriptor() {
        io.grpc.ServiceDescriptor result = serviceDescriptor;
        if (result == null) {
            synchronized (CallTargetGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor =
                        result =
                            io.grpc.ServiceDescriptor
                                .newBuilder(SERVICE_NAME)
                                .setSchemaDescriptor(new CallTargetFileDescriptorSupplier())
                                .addMethod(getCallTargetMethod())
                                .build();
                }
            }
        }
        return result;
    }
}
