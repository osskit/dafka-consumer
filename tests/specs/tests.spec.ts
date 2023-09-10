import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget, mockFaultyHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';
import {range} from 'lodash-es';
import pRetry from 'p-retry';
import delay from 'delay';

const topicRoutes = (topicRoutes: {topic: string; targetPath: string}[]) =>
    topicRoutes.map(({topic, targetPath}) => `${topic}:${targetPath}`).join(',');

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start();
    }, 5 * 60 * 1000);

    afterEach(async () => {
        orchestrator.stop();
    });

    describe('basic', () => {
        it('should produce and consume', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                },
                ['foo']
            );
            const target = await mockHttpTarget(orchestrator.target, '/consume', 200);

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [{value: JSON.stringify({data: 'foo'})}],
            });

            //assert
            await expect(getCalls(orchestrator.target, target)).resolves.toMatchSnapshot();
            await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
        });

        it('should produce and consume with regex patterns', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: '^([^.]+).foo', targetPath: '/consume'}]),
                },
                ['prefix.foo']
            );
            const target = await mockHttpTarget(orchestrator.target, '/consume', 200);

            //act
            await produce(orchestrator, {
                topic: 'prefix.foo',
                messages: [{value: JSON.stringify({data: 'foo'})}],
            });

            //assert
            await expect(getCalls(orchestrator.target, target)).resolves.toMatchSnapshot();
            await expect(getOffset(orchestrator.admin(), 'prefix.foo')).resolves.toBe(1);
        });

        it.only('should consume bursts of records', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                },
                ['foo']
            );
            await mockHttpTarget(orchestrator.target, '/consume', 200);

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: range(10000).map((_) => ({value: JSON.stringify({data: 'foo'})})),
            });
            await delay(30000);

            //assert
            await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(10000);
        });
    });

    describe('protocol', () => {
        it('should add record headers to target call', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                },
                ['foo']
            );
            const target = await mockHttpTarget(orchestrator.target, '/consume', 200);

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [
                    {
                        value: JSON.stringify({data: 'foo'}),
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

            //assert
            await expect(getCalls(orchestrator.target, target, true)).resolves.toMatchSnapshot();
        });

        it('copy body headers to record headers', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                    BODY_HEADERS_PATHS: 'bla,baz',
                },
                ['foo']
            );
            const target = await mockHttpTarget(orchestrator.target, '/consume', 200);

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [
                    {
                        value: JSON.stringify({data: 'foo', bla: {foo: 'bar', baz: null}}),
                    },
                ],
            });

            //assert
            await expect(getCalls(orchestrator.target, target, true)).resolves.toMatchSnapshot();
        });
    });

    describe('resiliency', () => {
        it('consumer should wait after connection error from target', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                    CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF: '5,8000000,2',
                    CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS: '8000000',
                },
                ['foo']
            );
            const target = await mockFaultyHttpTarget(orchestrator.target, '/consume');

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [{value: JSON.stringify({data: 'foo'})}],
            });
            await delay(20000);

            //assert
            await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(10);
            await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
        });

        it('consumer should wait after getting 503 response from target', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                    CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF: '50,5000,2',
                    CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS: '5000',
                },
                ['foo']
            );
            const target = await mockHttpTarget(orchestrator.target, '/consume', 503);

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [{value: JSON.stringify({data: 'foo'})}],
            });
            await delay(20000);

            //assert
            await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(10);
            await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
        });

        it('consumer should recover from socket error', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                    CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF: '50,5000,2',
                    CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS: '5000',
                },
                ['foo']
            );
            await mockFaultyHttpTarget(orchestrator.target, '/consume');

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [{value: JSON.stringify({data: 'foo'})}],
            });
            await delay(2000);
            await orchestrator.target.reset();
            const target = await mockHttpTarget(orchestrator.target, '/consume', 200);
            await delay(2000);

            //assert
            await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(1);
            await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
        });

        it('consumer should produce to retry topic', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                    RETRY_TOPIC: 'retry',
                    RETRY_PROCESS_WHEN_STATUS_CODE_MATCH: '511',
                    PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH: '511',
                    RETRY_POLICY_EXPONENTIAL_BACKOFF: '50,500,10',
                    RETRY_POLICY_MAX_DURATION: '1000',
                },
                ['foo', 'retry']
            );
            const target = await mockHttpTarget(orchestrator.target, '/consume', 511);

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [{value: JSON.stringify({data: 'foo'})}],
            });
            await delay(5000);

            //assert
            await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(10);
            await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
            await expect(orchestrator.consume('retry')).resolves.toMatchSnapshot();
        });

        it('consumer should produce to dead letter topic', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                    DEAD_LETTER_TOPIC: 'dead',
                    PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH: '428',
                },
                ['foo', 'dead']
            );
            const target = await mockHttpTarget(orchestrator.target, '/consume', 428);

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [{value: JSON.stringify({data: 'foo'})}],
            });
            await delay(5000);

            //assert
            await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(10);
            await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
            await expect(orchestrator.consume('dead')).resolves.toMatchSnapshot();
        });

        it('consumer should produce to dead letter topic when value is not valid JSON', async () => {
            //prepare
            await orchestrator.consumer(
                {
                    GROUP_ID: 'test',
                    TARGET_BASE_URL: 'http://mocks:8080',
                    TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                    DEAD_LETTER_TOPIC: 'dead',
                },
                ['foo', 'dead']
            );

            //act
            await produce(orchestrator, {
                topic: 'foo',
                messages: [{value: 'wat'}],
            });
            await delay(5000);

            //assert
            // await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(-1);
            await expect(orchestrator.consume('dead')).resolves.toMatchSnapshot();
        });
    });
});
