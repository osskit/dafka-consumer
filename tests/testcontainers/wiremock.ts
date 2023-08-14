import type {StartedNetwork, StartedTestContainer} from 'testcontainers';
import {GenericContainer} from 'testcontainers';
import {WireMockClient} from '@osskit/wiremock-client';

export const wiremock = async (network: StartedNetwork, alias = 'mocks') => {
    let container = new GenericContainer('wiremock/wiremock')
        .withExposedPorts(8080)
        .withNetwork(network)
        .withNetworkAliases(alias)
        .withCommand(['--verbose']);

    let startedContainer: StartedTestContainer | undefined;

    return {
        start: async () => {
            startedContainer = await container.start();
            return new WireMockClient(`http://localhost:${startedContainer.getMappedPort(8080)}`);
        },
        stop: () => startedContainer?.stop(),
    };
};
