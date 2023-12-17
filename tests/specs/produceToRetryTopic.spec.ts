import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';
import delay from 'delay';
import {topicRoutes} from '../services/topicRoutes.js';
import {consume} from '../services/consume.js';

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
                RETRY_TOPIC: 'retry',
                RETRY_PROCESS_WHEN_STATUS_CODE_MATCH: '511',
                PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH: '511',
                RETRY_POLICY_EXPONENTIAL_BACKOFF: '5,500,2',
                RETRY_POLICY_MAX_DURATION: '1000',
            },
            ['foo', 'retry']
        );
    }, 5 * 60 * 1000);

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
    });

    it('consumer should produce to retry topic', async () => {
        const target = await mockHttpTarget(orchestrator.wiremockClient, '/consume', 511, 'foo foo bar bar');

        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });

        await expect(getCalls(orchestrator.wiremockClient, target)).resolves.toHaveLength(10);
        await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(1);
        await expect(consume(orchestrator.kafkaClient, 'retry')).resolves.toMatchSnapshot();
    });
});
