import {StartedNetwork, StoppedTestContainer, Wait} from 'testcontainers';
import {GenericContainer} from 'testcontainers';
import {withThrow, withRetry} from '@osskit/fetch-enhancers';
import fs from 'node:fs';

const maxRetries = parseInt(process.env.MAX_RETRIES ?? '10');
const startupTimeout = parseInt(process.env.STARTUP_TIMEOUT ?? '60000');

const enhanchedFetch = withRetry(withThrow(fetch), {factor: 2, retries: maxRetries});

export interface ServiceContainer {
    stop: () => Promise<StoppedTestContainer>;
    ready: () => Promise<Response>;
}

export const dafkaConsumer = async (
    network: StartedNetwork,
    env: Record<string, string>
): Promise<ServiceContainer> => {
    const container = await new GenericContainer('bazel/src:image')
        .withExposedPorts(3000)
        .withNetwork(network)
        .withEnvironment({
            ...env,
            KAFKA_BROKER: 'kafka:9092',
            MONITORING_SERVER_PORT: '3000',
        })
        .withWaitStrategy(Wait.forLogMessage('kafka-consumer-test started'))
        .withStartupTimeout(startupTimeout)
        .start();

    await container.logs().then((logs) => logs.pipe(fs.createWriteStream('./tests/logs/service.logs', {})));

    const baseUrl = `http://localhost:${container.getMappedPort(3000)}`;

    return {
        stop: () => container.stop(),
        ready: () =>
            enhanchedFetch(`${baseUrl}/ready`, {
                method: 'get',
            }),
    };
};
