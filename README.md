<p align="center">
  <img width="300" height="300" src="https://user-images.githubusercontent.com/15312980/175078334-f284f44e-0366-4e24-8f09-5301b098ea64.svg"/>

  </p>
 
# dafka-consumer
Dockerized kafka consumer

## Introduction

## Usage & Examples

### docker-compose
```
version: '2.3'

services:
    consumer:
        build: osskit/dafka-consumer:5.1
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
version: '2.3'

services:
    consumer:
        build: osskit/dafka-consumer:5.1
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

    producer:
        image: osskit/dafka-producer:5
        ports:
            - 6000:6000
        environment:
            - PORT=6000
            - KAFKA_BROKER=kafka:9092
```

## License
MIT License
