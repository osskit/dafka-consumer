import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';
import {range} from 'lodash-es';
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

    it('should consume burst of records', async () => {
        await mockHttpTarget(orchestrator.wiremockClient, '/consume', 200);

        await produce(orchestrator, {
            topic: 'foo',
            messages: range(1000).map((_) => ({value: JSON.stringify({data: 'foo'})})),
        });

        await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(1000);
    });
});
