import {StartedNetwork, StoppedTestContainer, Wait} from 'testcontainers';
import {GenericContainer} from 'testcontainers';
import fs from 'node:fs';

const startupTimeout = parseInt(process.env.STARTUP_TIMEOUT ?? '60000');

export interface ServiceContainer {
    stop: () => Promise<StoppedTestContainer>;
}

export const dafkaConsumer = async (
    network: StartedNetwork,
    env: Record<string, string>
): Promise<ServiceContainer> => {
    const container = await new GenericContainer('bazel/src:image')
        .withExposedPorts(3000)
        .withNetwork(network)
        .withEnvironment(env)
        .withWaitStrategy(Wait.forLogMessage('consumer was assigned to partitions'))
        .withStartupTimeout(startupTimeout)
        .start();

    if (process.env.DEBUG) {
        await container.logs().then((logs) => logs.pipe(fs.createWriteStream('./tests/logs/service.log', {})));
    }

    return {
        stop: () => container.stop(),
    };
};
