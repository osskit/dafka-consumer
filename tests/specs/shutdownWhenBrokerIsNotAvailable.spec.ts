import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {mockHttpTarget} from '../services/target.js';
import {topicRoutes} from '../services/topicRoutes.js';
import delay from 'delay';

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start(
            {
                KAFKA_BROKER: 'foo',
                MONITORING_SERVER_PORT: '3000',
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
            },
            ['foo'],
            false
        );
    }, 5 * 60 * 1000);

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
    });

    it('should shutdown when broker is not available', async () => {
        await mockHttpTarget(orchestrator.wiremockClient, '/consume', 200);

        await delay(30000);

        await expect(orchestrator.dafkaConsumerInspect().then(({State}) => State.Status)).resolves.toBe('exited');
    });
});
