import type {StartedNetwork, StoppedTestContainer} from 'testcontainers';
import {GenericContainer} from 'testcontainers';

export interface ProducerClient {
    produce: (
        messages: {topic: string; key: string; value: string}[],
        headers?: Record<string, string>
    ) => Promise<Response>;
    ready: () => Promise<Response>;
}

export interface ProducerContainer {
    stop: () => Promise<StoppedTestContainer>;
    client: ProducerClient;
}

export const producer = async (network: StartedNetwork): Promise<ProducerContainer> => {
    const container = await new GenericContainer('osskit/dafka-producer:5.1')
        .withExposedPorts(3001)
        .withNetwork(network)
        .withEnvironment({
            KAFKA_BROKER: 'kafka:9092',
            PORT: '3001',
            MONITORING_SERVER_PORT: '3001',
        })
        .start();

    if (process.env.VERBOSE) {
        const logs = await container.logs();
        logs.pipe(process.stdout);
    }

    const producerUrl = `http://localhost:${container.getMappedPort(3000)}`;

    return {
        stop: () => container.stop(),
        client: {
            produce: (messages, headers) =>
                fetch(`${producerUrl}/produce`, {
                    method: 'post',
                    body: JSON.stringify(messages),
                    headers: {'Content-Type': 'application/json', ...headers},
                }),
            ready: () =>
                fetch(`${producerUrl}/ready`, {
                    method: 'get',
                }),
        },
    };
};
