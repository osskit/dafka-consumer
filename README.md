# kafka-consumer-java

## Generate new Proto

```
./protoc --proto_path=$PWD/dafka-grpc-target --java_out=$PWD/kafka-consumer-java/src/main/java $PWD/dafka-grpc-target/message.proto
```
