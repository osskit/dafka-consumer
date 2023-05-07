import type {StartedNetwork, StoppedTestContainer} from 'testcontainers';
import {GenericContainer} from 'testcontainers';
import {withThrow, withRetry} from '@osskit/fetch-enhancers';

const enhanchedFetch = withRetry(withThrow(fetch), {factor: 2, retries: 10});

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
        .start();

    if (process.env.VERBOSE) {
        const logs = await container.logs();
        logs.pipe(process.stdout);
    }

    const baseUrl = `http://localhost:${container.getMappedPort(3000)}`;

    return {
        stop: () => container.stop(),
        ready: () =>
            enhanchedFetch(`${baseUrl}/ready`, {
                method: 'get',
            }),
    };
};
