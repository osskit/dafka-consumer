<p align="center">
  <img width="300" height="300" src="https://user-images.githubusercontent.com/15312980/175078334-f284f44e-0366-4e24-8f09-5301b098ea64.svg"/>

  </p>
 
# dafka-consumer
Dockerized kafka consumer

## Introduction
Dafka-consumer is a dockerized Kafka consumer used to abstract consuming messages from a kafka topic.

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

## Parameters

Container images are configured using parameters passed at runtime.

| Parameter | Default Values | Description |
| :----: | --- | ---- |
| `KAFKA_BROKER=https://kafka-broker.com` | URL for the Kafka Broker | |
| `TARGET_BASE_URL=` | | |
| `GROUP_ID=0` |  #optional | | 
| `TOPICS_ROUTES="none"` | #optional | |
| `TARGET_TIMEOUT_MS=false` | #optional | |
| `POLL_TIMEOUT=1000` | #optional | |
| `MAX_POLL_RECORDS=50` | #optional | |
| `SESSION_TIMEOUT=10000` | #optional | |
| `RETRY_PROCESS_WHEN_STATUS_CODE_MATCH=5[0-9][0-9]` | #optional | |
| `PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH=408` | #optional | |
| `PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH=4[0-9][0-79]` | #optional | |
| `RETRY_POLICY_EXPONENTIAL_BACKOFF=50,5000,10` | #optional | |
| `RETRY_TOPIC=null` | #optional | |
| `DEAD_LETTER_TOPIC=null` | #optional | |
| `PROCESSING_DELAY=0` | #optional | |
| `MONITORING_SERVER_PORT=0` | #optional | | 
| `TARGET_HEALTHCHECK=null` | #optional | | 
| `USE_SASL_AUTH=false` | #optional | |
| `SASL_USERNAME` | #optional | | 
| `SASL_PASSWORD` | #optional | | 
| `TRUSTSTORE_FILE_PATH=null` | #optional | |
| `TRUSTSTORE_PASSWORD=null` | #optional | | 
| `USE_PROMETHEUS=false` | #optional | |
| `PROMETHEUS_BUCKETS=0.003,0.03,0.1,0.3,1.5,10` | #optional | |



## License
MIT License
