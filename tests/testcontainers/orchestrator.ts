import {Network} from 'testcontainers';
import {dafkaConsumer} from './dafkaConsumer.js';
import {kafka} from './kafka.js';
import {wiremock} from './wiremock.js';
import {WireMockClient} from '@osskit/wiremock-client';
import {Kafka} from 'kafkajs';
import Dockerode from 'dockerode';

export interface Orchestrator {
    kafkaClient: Kafka;
    wiremockClient: WireMockClient;
    dafkaConsumerInspect: () => Promise<Dockerode.ContainerInspectInfo>;
    stop: () => Promise<void>;
}

export const start = async (
    env: Record<string, string>,
    topics: string[],
    waitForAssignedPartitions: boolean = true
) => {
    const network = await new Network().start();

    const {client: kafkaClient, stop: stopKafka} = await kafka(network, topics);
    const {stop: stopConsumer, inspect: dafkaConsumerInspect} = await dafkaConsumer(
        network,
        env,
        waitForAssignedPartitions
    );
    const {client: wiremockClient, stop: stopWiremock} = await wiremock(network);

    return {
        kafkaClient,
        wiremockClient,
        dafkaConsumerInspect,
        stop: async () => {
            await stopConsumer();
            await stopWiremock();
            await stopKafka();
            await network.stop();
        },
    };
};
