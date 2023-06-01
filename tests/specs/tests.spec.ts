import {HttpMethod} from '@osskit/wiremock-client';
import type {Orchestrator, KafkaOrchestrator} from '../testcontainers/orchestrator.js';
import {start as startKafka} from '../testcontainers/orchestrator.js';
import {range} from 'lodash-es';
import pRetry from 'p-retry';
import {KafkaMessage, Producer} from 'kafkajs';

describe('tests', () => {
    let kafkaOrchestrator: KafkaOrchestrator;
    let orchestrator: Orchestrator;
    let producer: Producer;

    beforeAll(async () => {
        kafkaOrchestrator = await startKafka();
    }, 1800000);

    afterAll(async () => {
        await kafkaOrchestrator.stop();
    }, 1800000);

    afterEach(async () => {
        if (producer) {
            await producer.disconnect();
        }
        await orchestrator.stop();
    });

    const start = async (
        topics: string[],
        topicRoutes: {topic: string; targetPath: string}[],
        consumerSettings?: Record<string, string>
    ) => {
        orchestrator = await kafkaOrchestrator.startOrchestrator({
            GROUP_ID: 'test',
            TARGET_BASE_URL: 'http://mocks:8080',
            TOPICS_ROUTES: topicRoutes.map(({topic, targetPath}) => `${topic}:${targetPath}`).join(','),
            ...consumerSettings,
        });

        const admin = kafkaOrchestrator.kafkaClient.admin();

        await admin.createTopics({topics: topics.map((topic) => ({topic}))});

        await orchestrator.consumerReady();

        producer = kafkaOrchestrator.kafkaClient.producer();
        await producer.connect();
    };

    const mockHttpTarget = (url: string, status: number, faulty = false) =>
        orchestrator.wireMockClient.createMapping({
            request: {
                url: url,
                method: HttpMethod.Post,
            },
            response: faulty
                ? {
                      //@ts-ignore
                      fault: 'CONNECTION_RESET_BY_PEER',
                  }
                : {
                      status,
                  },
        });
    it('should produce and consume', async () => {
        await start(['foo'], [{topic: 'foo', targetPath: '/consume'}]);

        const consumerMapping = await mockHttpTarget('/consume', 200);

        await producer.send({topic: 'foo', messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});

        const calls = await orchestrator.wireMockClient.waitForCalls(consumerMapping);
        expect(calls).toHaveLength(1);

        expect(calls[0]).toMatchSnapshot({
            headers: {'x-record-timestamp': expect.any(String), 'x-record-offset': expect.any(String)},
            loggedDate: expect.any(Number),
        });
    }, 1800000);

    it('should produce and consume with regex patterns', async () => {
        await start(['lol.bar'], [{topic: '^([^.]+).bar', targetPath: '/consume'}]);

        const consumerMapping = await mockHttpTarget('/consume', 200);

        await producer.send({topic: 'lol.bar', messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});

        const calls = await orchestrator.wireMockClient.waitForCalls(consumerMapping);
        expect(calls).toHaveLength(1);
    }, 1800000);

    it('should add record headers to target call', async () => {
        await start(['foo1'], [{topic: 'foo1', targetPath: '/consume'}]);

        const consumerMapping = await mockHttpTarget('/consume', 200);

        await producer.send({
            topic: 'foo1',
            messages: [
                {
                    value: JSON.stringify({data: 'foo'}),
                    key: 'thekey',
                    headers: {
                        'x-request-id': '123',
                        'x-b3-traceid': '456',
                        'x-b3-spanid': '789',
                        'x-b3-parentspanid': '101112',
                        'x-b3-sampled': '1',
                        'x-b3-flags': '1',
                        'x-ot-span-context': 'foo',
                    },
                },
            ],
        });

        const calls = await orchestrator.wireMockClient.waitForCalls(consumerMapping);
        expect(calls).toHaveLength(1);
        expect(calls[0]).toMatchSnapshot({
            headers: {'x-record-timestamp': expect.any(String), 'x-record-offset': expect.any(String)},
            loggedDate: expect.any(Number),
        });
    }, 1800000);

    it('should transform and add cloud event headers to target call', async () => {
        await start(['foo2'], [{topic: 'foo2', targetPath: '/consume'}]);

        const consumerMapping = await mockHttpTarget('/consume', 200);

        await producer.send({
            topic: 'foo2',
            messages: [
                {
                    value: JSON.stringify({data: 'foo'}),
                    key: 'thekey',
                    headers: {
                        'x-request-id': 'bla',
                        random_header: 'random',
                        ce_type: 'type',
                        ce_id: 'id',
                        ce_specversion: '1',
                        ce_source: 'source',
                        ce_time: '123456',
                    },
                },
            ],
        });

        const calls = await orchestrator.wireMockClient.waitForCalls(consumerMapping);
        expect(calls).toHaveLength(1);
        expect(calls[0]).toMatchSnapshot({
            headers: {'x-record-timestamp': expect.any(String), 'x-record-offset': expect.any(String)},
            loggedDate: expect.any(Number),
        });
    }, 1800000);

    it('should consume bursts of records', async () => {
        await start(['foo3'], [{topic: 'foo3', targetPath: '/consume'}]);

        const consumerMapping = await mockHttpTarget('/consume', 200);

        const recordsCount = 1000;

        await producer.send({
            topic: 'foo3',
            messages: range(recordsCount).map((_) => ({value: JSON.stringify({data: 'foo'})})),
        });

        const calls = await pRetry(
            async () => {
                const c = await orchestrator.wireMockClient.waitForCalls(consumerMapping);

                if (c.length !== recordsCount) {
                    throw new Error(`invalid call count: ${c.length}`);
                }

                return c;
            },
            {retries: 10}
        );

        expect(calls).toHaveLength(recordsCount);
    }, 1800000);

    it('consumer should produce to dead letter topic when target response is 400', async () => {
        const deadLetterTopic = 'dead-letter';

        await start(['foo4', deadLetterTopic], [{topic: 'foo4', targetPath: '/consume'}], {
            DEAD_LETTER_TOPIC: deadLetterTopic,
        });

        const consumerMapping = await mockHttpTarget('/consume', 400);

        await producer.send({topic: 'foo4', messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});

        const calls = await orchestrator.wireMockClient.waitForCalls(consumerMapping);

        expect(calls).toHaveLength(1);

        // because we need Hamsa Hamsa Hamsa for tests to work
        const consumer = kafkaOrchestrator.kafkaClient.consumer({groupId: 'test-555'});

        await consumer.subscribe({topic: deadLetterTopic, fromBeginning: true});

        const consumedMessage = await new Promise<KafkaMessage>((resolve) => {
            consumer.run({
                eachMessage: async ({message}) => resolve(message),
            });
        });

        await consumer.disconnect();

        expect(JSON.parse(consumedMessage.value?.toString() ?? '{}')).toMatchSnapshot();
        expect(
            Object.fromEntries(Object.entries(consumedMessage.headers!).map(([key, value]) => [key, value?.toString()]))
        ).toMatchSnapshot();
    }, 1800000);

    it('consumer should produce to retry topic when target response is 500', async () => {
        const retryTopic = 'retry';
        const topic = 'foo89';
        await start([topic, retryTopic], [{topic, targetPath: '/consume'}], {
            RETRY_TOPIC: retryTopic,
        });

        const consumerMapping = await mockHttpTarget('/consume', 500);

        await producer.send({topic, messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});

        const calls = await orchestrator.wireMockClient.waitForCalls(consumerMapping);

        expect(calls).toHaveLength(2);

        // because we need Hamsa Hamsa Hamsa for tests to work
        const consumer = kafkaOrchestrator.kafkaClient.consumer({groupId: 'test-555'});

        await consumer.subscribe({topic: retryTopic, fromBeginning: true});

        const consumedMessage = await new Promise<KafkaMessage>((resolve) => {
            consumer.run({
                eachMessage: async ({message}) => resolve(message),
            });
        });

        await consumer.disconnect();

        expect(JSON.parse(consumedMessage.value?.toString() ?? '{}')).toMatchSnapshot();
        expect(
            Object.fromEntries(Object.entries(consumedMessage.headers!).map(([key, value]) => [key, value?.toString()]))
        ).toMatchSnapshot();
    }, 1800000);

    it('consumer should produce to retry topic on an unexpected error', async () => {
        const retryTopic = 'retry-345345';
        const topic = `foo-45445`;
        await start([topic, retryTopic], [{topic, targetPath: '/consume'}], {
            RETRY_TOPIC: retryTopic,
        });

        await mockHttpTarget('/consume', 200, true);

        await producer.send({topic, messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});

        // because we need Hamsa Hamsa Hamsa for tests to work
        const consumer = kafkaOrchestrator.kafkaClient.consumer({groupId: 'test-555'});

        await consumer.subscribe({topic: retryTopic, fromBeginning: true});

        const consumedMessage = await new Promise<KafkaMessage>((resolve) => {
            consumer.run({
                eachMessage: async ({message}) => resolve(message),
            });
        });

        await consumer.disconnect();
        expect(JSON.parse(consumedMessage.value?.toString() ?? '{}')).toMatchSnapshot();
        expect(
            Object.fromEntries(Object.entries(consumedMessage.headers!).map(([key, value]) => [key, value?.toString()]))
        ).toMatchSnapshot();

        const admin = kafkaOrchestrator.kafkaClient.admin();

        await admin.connect();

        const metadata = await admin.fetchOffsets({groupId: 'test', topics: [topic]});

        admin.disconnect();

        expect(metadata).toMatchSnapshot();
    }, 1800000);
});
