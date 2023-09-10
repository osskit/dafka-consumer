import type {StartedNetwork} from 'testcontainers';
import {GenericContainer} from 'testcontainers';
import {WireMockClient} from '@osskit/wiremock-client';

export const wiremock = async (network: StartedNetwork) => {
    let container = new GenericContainer('wiremock/wiremock')
        .withExposedPorts(8080)
        .withNetwork(network)
        .withNetworkAliases('mocks')
        .withCommand(['--verbose']);

    let startedContainer = await container.start();
    let client = new WireMockClient(`http://localhost:${startedContainer.getMappedPort(8080)}`);

    return {
        client,
        stop: () => startedContainer.stop(),
    };
};
