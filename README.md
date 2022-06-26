<p align="center">
  <img width="300" height="200" src="https://user-images.githubusercontent.com/15312980/175078334-f284f44e-0366-4e24-8f09-5301b098ea64.svg"/>

  </p>
 
<div align="center">
Dockerized kafka consumer
  
</div>

## Overview
Dafka-consumer is a dockerized Kafka consumer used to abstract consuming messages from a kafka topic.

Using dafka-consumer, consuming messages is as simple as getting a POST request to your service, with the body of the request being the kafka message.

### Motivation
Why use this over just a Kafka client?
* Abstracts away the messaging layer, could be replaced with RabbitMQ or any other consumer.
* Separates configuration, everything that's related to Kafka is encapsulated in Dafka and not the service itself.
* When testing your service you only test your service's logic and not the messaging layer implementation details.

<img width="754" alt="image" src="https://user-images.githubusercontent.com/15312980/175814180-7ca374ac-da3b-4ea4-a482-9396bfbe11c4.png">


## Usage & Examples

### docker-compose
```
version: '3.9'

services:
    consumer:
        image: osskit/dafka-consumer
        ports:
            - 4001:4001
        environment:
            - KAFKA_BROKER=kafka:9092
            - TARGET_BASE_URL=http://target:2000/
            - TOPICS_ROUTES=foo:consume,bar:consume,^([^.]+).bar:consume
            - RETRY_TOPIC=retry
            - DEAD_LETTER_TOPIC=dead-letter
            - GROUP_ID=consumer_1
            - MONITORING_SERVER_PORT=4001
```

In joint with `dafka-producer`:

```
version: '3.9'

services:
    consumer:
        image: osskit/dafka-consumer
        ports:
            - 4001:4001
        environment:
            - KAFKA_BROKER=kafka:9092
            - TARGET_BASE_URL=http://target:2000/
            - TOPICS_ROUTES=foo:consume,bar:consume,^([^.]+).bar:consume
            - RETRY_TOPIC=retry # optional
            - DEAD_LETTER_TOPIC=dead-letter # optional
            - GROUP_ID=consumer_1

    producer:
        image: osskit/dafka-producer
        ports:
            - 6000:6000
        environment:
            - PORT=6000
            - KAFKA_BROKER=kafka:9092
```

### Kubernetes
You can use the provided [Helm Chart](https://github.com/osskit/dafka-consumer-helm-chart), this gives you a `Deployment` separated from your service's `Pod`.

It's also possible to use this as a `Sidecar`.

## Parameters

Container images are configured using parameters passed at runtime.

| Parameter | Default Values | Description |
| :----: | --- | ---- |
| `KAFKA_BROKER` | `required` | URL for the Kafka Broker |
| `TARGET_BASE_URL` | `required` | The target's HTTP POST endpoint |
| `GROUP_ID` |  `required` | | 
| `TOPICS_ROUTES` | `required` | A map between topics and their endpoint routes (e.g `topic:/consume`) |
| `TARGET_TIMEOUT_MS` | `2147483647` | |
| `POLL_TIMEOUT` | `1000` | |
| `MAX_POLL_RECORDS` | `50` | |
| `SESSION_TIMEOUT` | `10000` | |
| `RETRY_PROCESS_WHEN_STATUS_CODE_MATCH` | `5[0-9][0-9]` | Retry to process the record if the returning status code matches the regex |
| `PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH` | `408` | Produce to retry topic on matching status code |
| `PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH` | `4[0-9][0-79]` | Produce to dead letter topic when matching status code regex |
| `RETRY_POLICY_EXPONENTIAL_BACKOFF` | `50,5000,10` | |
| `RETRY_TOPIC` | `null` | Retry topic name |
| `DEAD_LETTER_TOPIC` | `null` | Dead letter topic name |
| `PROCESSING_DELAY` | `0` | |
| `MONITORING_SERVER_PORT` | `0` | | 
| `TARGET_HEALTHCHECK` | `null` | Target's healthcheck endpoint to verify it's alive | 
| `USE_SASL_AUTH=false` | `false` | use SASL authentication |
| `SASL_USERNAME` | `required` if `USE_SASL_AUTH=true` | SASL username to authenticate | 
| `SASL_PASSWORD` | `required` if `USE_SASL_AUTH=true` | SASL password to authenticate | 
| `TRUSTSTORE_FILE_PATH` | `null` | |
| `TRUSTSTORE_PASSWORD` | `required` if `TRUSTSTORE_FILE_PATH != null` | | 
| `USE_PROMETHEUS` | `false` | Export metrics to Prometheus |
| `PROMETHEUS_BUCKETS` | `0.003,0.03,0.1,0.3,1.5,10` | A list of Prometheus buckets to use |



## License
MIT License
