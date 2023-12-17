import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockFaultyHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';
import delay from 'delay';
import {topicRoutes} from '../services/topicRoutes.js';

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start(
            {
                KAFKA_BROKER: 'kafka:9092',
                MONITORING_SERVER_PORT: '3000',
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF: '5,8000000,2',
                CONNECTION_FAILURE_RETRY_POLICY_MAX_RETRIES: '5',
            },
            ['foo']
        );
    }, 5 * 60 * 1000);

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
    });

    it('consumer should wait after connection error from target', async () => {
        const target = await mockFaultyHttpTarget(orchestrator.wiremockClient, '/consume');

        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });

        await expect(getCalls(orchestrator.wiremockClient, target)).resolves.toHaveLength(5);
        await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(1);
    });
});
