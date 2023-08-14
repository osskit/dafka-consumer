import {HttpMethod} from '@osskit/wiremock-client';
import type {Orchestrator, KafkaOrchestrator} from '../testcontainers/orchestrator.js';
import {start as startKafka} from '../testcontainers/orchestrator.js';
import {range} from 'lodash-es';
import pRetry from 'p-retry';
import {KafkaMessage, Producer} from 'kafkajs';
import delay from 'delay';

describe('tests', () => {
    let kafkaOrchestrator: KafkaOrchestrator;
    let orchestrator: Orchestrator;
    let producer: Producer;

    beforeAll(async () => {
        kafkaOrchestrator = await startKafka();
    }, 18000);

    afterAll(async () => {
        await kafkaOrchestrator.stop();
    }, 18000);

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
            RETRY_POLICY_EXPONENTIAL_BACKOFF: '1,5,2',
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
                      fault: 'CONNECTION_RESET_BY_PEER',
                  }
                : {
                      status,
                  },
        });

    const assertOffset = async (topic: string) => {
        const admin = kafkaOrchestrator.kafkaClient.admin();

        await admin.connect();

        const metadata = await admin.fetchOffsets({groupId: 'test', topics: [topic]});

        admin.disconnect();

        expect(metadata).toMatchSnapshot();
    };

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

        await assertOffset('foo');
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

    it('consumer should produce to deadLetterTopic topic when value is not valid JSON', async () => {
        const deadLetterTopic = 'deadLetterTopic333';
        const topic = 'foo89';
        await start([topic, deadLetterTopic], [{topic, targetPath: '/consume'}], {
            DEAD_LETTER_TOPIC: deadLetterTopic,
        });

        await producer.send({topic, messages: [{value: '', key: 'thekey'}]});

        const consumer = kafkaOrchestrator.kafkaClient.consumer({groupId: 'test-555'});

        await consumer.subscribe({topic: deadLetterTopic, fromBeginning: true});

        const consumedMessage = await new Promise<KafkaMessage>((resolve) => {
            consumer.run({
                eachMessage: async ({message}) => resolve(message),
            });
        });

        await consumer.disconnect();

        expect(consumedMessage.value?.toString()).toMatchSnapshot();
        expect(
            Object.fromEntries(Object.entries(consumedMessage.headers!).map(([key, value]) => [key, value?.toString()]))
        ).toMatchSnapshot();
    }, 1800000);

    it('consumer should produce to retry topic when target response is 500', async () => {
        const retryTopic = 'retry';
        const topic = 'foo89';
        await start([topic, retryTopic], [{topic, targetPath: '/consume'}], {
            RETRY_TOPIC: retryTopic,
            PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH: '500',
        });

        const consumerMapping = await mockHttpTarget('/consume', 500);

        await producer.send({topic, messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});

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

        const calls = await orchestrator.wireMockClient.waitForCalls(consumerMapping);

        expect(calls).toHaveLength(3);
    }, 1800000);

    it('consumer should produce to retry topic on an unexpected error', async () => {
        const retryTopic = 'retry-345345';
        const topic = `foo-45445`;
        await start([topic, retryTopic], [{topic, targetPath: '/consume'}], {
            RETRY_TOPIC: retryTopic,
        });

        await mockHttpTarget('/consume', 200, true);

        await producer.send({topic, messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});

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

        await assertOffset(topic);
    }, 1800000);

    it('should support body headers them to headers of the record', async () => {
        await start(['foo2'], [{topic: 'foo2', targetPath: '/consume'}], {BODY_HEADERS_PATHS: 'headers,bla'});

        const consumerMapping = await mockHttpTarget('/consume', 200);

        await producer.send({
            topic: 'foo2',
            messages: [
                {
                    value: JSON.stringify({data: 'foo', bla: {foo: 'bar'}, headers: {'x-request-id': 'bla'}}),
                    key: 'thekey',
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

    it('should support body headers them to headers of the record even with empty header value', async () => {
        await start(['foo2'], [{topic: 'foo2', targetPath: '/consume'}], {BODY_HEADERS_PATHS: 'headers,bla'});

        const consumerMapping = await mockHttpTarget('/consume', 200);

        await producer.send({
            topic: 'foo2',
            messages: [
                {
                    value: JSON.stringify({data: 'foo', bla: {foo: null}, headers: {'x-request-id': 'requestId'}}),
                    key: 'thekey',
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

    it.only('consumer should wait after connection error from target', async () => {
        const targetPath = '/consume';
        const topic = 'wait-foo';
        await start([topic], [{topic, targetPath}], {
            CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF: '1000,10000,2',
            TARGET_BASE_URL: 'http://transientMocks:8080',
        });
        await producer.send({topic, messages: [{value: JSON.stringify({data: 'foo'}), key: 'thekey'}]});
        const transientWireMockClient = await orchestrator.startTransientWireMock();
        const consumerMapping = await transientWireMockClient.createMapping({
            request: {
                url: targetPath,
                method: HttpMethod.Post,
            },
            response: {
                status: 200,
            },
        });

        await delay(2000);
        const calls = await transientWireMockClient.waitForCalls(consumerMapping);

        expect(calls).toHaveLength(1);
    }, 1800000);
});
