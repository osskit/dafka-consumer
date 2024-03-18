import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';
import delay from 'delay';

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
                TARGET_PROCESS_TYPE: 'batch',
                RECORD_FILTER_FIELD: 'type',
                RECORD_FILTER_VALUE: 'created',
                RECORD_PROJECT_FIELD: 'data',
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

    it('batch consume with record filter and project', async () => {
        const target1 = await mockHttpTarget(orchestrator.wiremockClient, '/consume', 200);

        await produce(orchestrator, {
            topic: 'foo',
            messages: [
                {key: '1', value: JSON.stringify({type: 'created', data: 'foo1'})},
                {key: '2', value: JSON.stringify({type: 'created', data: 'foo2'})},
                {key: '3', value: JSON.stringify({type: 'deleted', data: 'foo3'})},
            ],
        });

        await delay(5000);

        await expect(getCalls(orchestrator.wiremockClient, target1)).resolves.toMatchSnapshot();
    });
});
