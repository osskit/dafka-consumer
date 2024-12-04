import {StartedNetwork, StoppedTestContainer, Wait} from 'testcontainers';
import {GenericContainer} from 'testcontainers';
import fs from 'node:fs';
import {getContainerRuntimeClient} from 'testcontainers';
import Dockerode from 'dockerode';

export interface ServiceContainer {
    stop: () => Promise<StoppedTestContainer>;
    inspect: () => Promise<Dockerode.ContainerInspectInfo>;
}

export const dafkaConsumer = async (
    network: StartedNetwork,
    env: Record<string, string>,
    waitForAssignedPartitions: boolean
): Promise<ServiceContainer> => {
    const container = await new GenericContainer('bazel/src:image')
        .withExposedPorts(3000)
        .withNetwork(network)
        .withEnvironment({...env, COMMIT_INTERVAL_MS: '1'})
        .withWaitStrategy(
            Wait.forLogMessage(
                waitForAssignedPartitions
                    ? 'consumer was assigned to partitions'
                    : `target healthcheck passed successfully`
            )
        )
        .withStartupTimeout(parseInt(process.env.STARTUP_TIMEOUT ?? '60000'))
        .start();

    if (process.env.DEBUG) {
        try {
            fs.truncateSync('service.log', 0);
        } catch (err) {
            fs.writeFileSync('service.log', '', {flag: 'wx'});
        }
        await container.logs().then((logs) => logs.pipe(fs.createWriteStream('service.log')));
    }

    return {
        stop: () => container.stop(),
        inspect: async () => {
            const containerRuntimeClient = await getContainerRuntimeClient();
            return containerRuntimeClient.container.getById(container.getId()).inspect();
        },
    };
};
