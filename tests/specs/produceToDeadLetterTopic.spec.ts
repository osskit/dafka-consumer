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
                DEAD_LETTER_TOPIC: 'dead',
                PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH: '428',
            },
            ['foo', 'dead']
        );
    }, 5 * 60 * 1000);

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
    });

    it('consumer should produce to dead letter topic', async () => {
        const target = await mockHttpTarget(orchestrator.wiremockClient, '/consume', 428, 'foo foo bar bar');

        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });
        await delay(5000);

        await expect(getCalls(orchestrator.wiremockClient, target)).resolves.toHaveLength(1);
        await expect(getOffset(orchestrator.kafkaClient, 'foo', 1)).resolves.toBe(true);
        await expect(consume(orchestrator.kafkaClient, 'dead')).resolves.toMatchSnapshot();
    });
});
