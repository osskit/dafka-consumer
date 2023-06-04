import type {StartedNetwork} from 'testcontainers';
import {GenericContainer} from 'testcontainers';
import {WireMockClient} from '@osskit/wiremock-client';

export const wiremock = async (network: StartedNetwork) => {
    const container = await new GenericContainer('wiremock/wiremock')
        .withExposedPorts(8080)
        .withNetworkMode(network.getName())
        .withNetworkAliases('mocks')
        .withCommand(['--verbose'])
        .start();

    return {
        stop: () => container.stop(),
        client: new WireMockClient(`http://localhost:${container.getMappedPort(8080)}`),
    };
};
