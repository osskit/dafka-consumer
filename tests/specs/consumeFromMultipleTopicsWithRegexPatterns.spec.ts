import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';
import {sortBy} from 'lodash-es';

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start(
            {
                KAFKA_BROKER: 'kafka:9092',
                MONITORING_SERVER_PORT: '3000',
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo.*', targetPath: '/consume'}]),
            },
            ['foo', 'foo-7ff01d48-804d-4bd6-9f4b-ff6b9ef727df', 'foo-5f7d747e-6a8f-4f11-b001-2f5a658a3a35']
        );
    }, 5 * 60 * 1000);

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
    });

    it('consume from multiple topics with regex patterns', async () => {
        const target = await mockHttpTarget(orchestrator.wiremockClient, '/consume', 200);

        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });
        await produce(orchestrator, {
            topic: 'foo-7ff01d48-804d-4bd6-9f4b-ff6b9ef727df',
            messages: [{value: JSON.stringify({data: 'foo1'})}],
        });
        await produce(orchestrator, {
            topic: 'foo-5f7d747e-6a8f-4f11-b001-2f5a658a3a35',
            messages: [{value: JSON.stringify({data: 'foo2'})}],
        });

        await expect(
            getCalls(orchestrator.wiremockClient, target).then((calls) => sortBy(calls, 'body.data'))
        ).resolves.toMatchSnapshot();
        await expect(getOffset(orchestrator.kafkaClient, 'foo', 1)).resolves.toBe(true);
        await expect(getOffset(orchestrator.kafkaClient, 'foo-7ff01d48-804d-4bd6-9f4b-ff6b9ef727df', 1)).resolves.toBe(
            true
        );
        await expect(getOffset(orchestrator.kafkaClient, 'foo-5f7d747e-6a8f-4f11-b001-2f5a658a3a35', 1)).resolves.toBe(
            true
        );
    });
});
