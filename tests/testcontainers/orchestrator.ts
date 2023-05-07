import {Network} from 'testcontainers';
import {dafkaConsumer} from './dafkaConsumer.js';
import {kafka} from './kafka.js';
import {wiremock} from './wiremock.js';

import {WireMockClient} from '@osskit/wiremock-client';
import {Kafka} from 'kafkajs';

export interface Orchestrator {
    stop: () => Promise<void>;
    kafkaClient: Kafka;
    wireMockClient: WireMockClient;
    consumerReady: () => Promise<Response>;
}

export const start = async (dafkaEnv: Record<string, string>): Promise<Orchestrator> => {
    const network = await new Network().start();

    const [
        {client: wireMockClient, stop: stopWiremock},
        {client: kafkaClient, stop: stopKafka},
        {ready: consumerReady, stop: stopService},
    ] = await Promise.all([wiremock(network), kafka(network), dafkaConsumer(network, dafkaEnv)]);

    return {
        async stop() {
            await Promise.all([stopService(), stopWiremock(), stopKafka()]);
        },
        kafkaClient,
        wireMockClient,
        consumerReady,
    };
};
