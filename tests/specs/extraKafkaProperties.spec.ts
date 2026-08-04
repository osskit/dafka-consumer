import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {mockHttpTarget} from '../services/target.js';
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
                DEAD_LETTER_TOPIC: 'dead',
                PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH: '428',
                KAFKA_PRODUCER_PROPERTY_MAX_REQUEST_SIZE: '1024',
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

    // produceToDeadLetterTopic.spec.ts is the same scenario without the clamp and ends with offset 1
    it('should apply extra producer properties to the dead letter producer', async () => {
        await mockHttpTarget(orchestrator.wiremockClient, '/consume', 428, 'foo foo bar bar');

        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'x'.repeat(4096)})}],
        });
        await delay(5000);

        await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(-1);
    });
});
