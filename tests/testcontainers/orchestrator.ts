import {Network, StoppedTestContainer} from 'testcontainers';
import {consumer} from './consumer.js';
import {kafka} from './kafka.js';
import {wiremock} from './wiremock.js';
import {WireMockClient} from '@osskit/wiremock-client';
import {Admin, KafkaMessage, Producer} from 'kafkajs';

export interface Orchestrator {
    consumer: (env: Record<string, string>, topics: string[]) => Promise<void>;
    producer: () => Promise<Producer>;
    target: WireMockClient;
    admin: () => Admin;
    consume: (topic: string, parse?: boolean) => Promise<any>;
    stop: () => Promise<void>;
}

export const start = async () => {
    const network = await new Network().start();

    const {client: kafkaClient, stop: stopKafka} = await kafka(network);
    const {client: target, stop: stopWiremock} = await wiremock(network);

    let stopConsumer: () => Promise<StoppedTestContainer>;
    let producer: Producer;
    return {
        consumer: async (env: Record<string, string>, topics: string[]) => {
            const admin = kafkaClient.admin();
            await admin.createTopics({topics: topics.map((topic) => ({topic}))});
            const {stop} = await consumer(network, env);
            stopConsumer = stop;
        },
        producer: async () => {
            producer = kafkaClient.producer();
            await producer.connect();
            return producer;
        },
        target,
        admin: () => kafkaClient.admin(),
        consume: async (topic: string, parse = true) => {
            const consumer = kafkaClient.consumer({groupId: 'orchestrator'});
            await consumer.subscribe({topic: topic, fromBeginning: true});
            const consumedMessage = await new Promise<KafkaMessage>((resolve) => {
                consumer.run({
                    eachMessage: async ({message}) => resolve(message),
                });
            });
            await consumer.disconnect();
            const value = parse
                ? JSON.parse(consumedMessage.value?.toString() ?? '{}')
                : consumedMessage.value?.toString();
            const headers = Object.fromEntries(
                Object.entries(consumedMessage.headers!).map(([key, value]) => [key, value?.toString()])
            );
            return {value, headers};
        },
        stop: async () => {
            await producer.disconnect();
            await stopConsumer();
            await stopWiremock();
            await stopKafka();
            await network.stop();
        },
    };
};
