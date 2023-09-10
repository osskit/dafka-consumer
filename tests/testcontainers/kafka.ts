import {StartedNetwork, Wait} from 'testcontainers';
import {KafkaContainer} from '@testcontainers/kafka';
import {Kafka, logLevel} from 'kafkajs';

export const kafka = async (network: StartedNetwork) => {
    const container = await new KafkaContainer('confluentinc/cp-kafka:7.2.2')
        .withNetwork(network)
        .withNetworkAliases('kafka')
        .withWaitStrategy(
            Wait.forLogMessage('Registered broker 1 at path /brokers/ids/1 with addresses: BROKER://kafka:9092')
        )
        .start();

    const client = new Kafka({
        logLevel: logLevel.NOTHING,
        brokers: [`${container.getHost()}:${container.getMappedPort(9093)}`],
    });

    return {
        stop: () => container.stop(),
        client,
    };
};
