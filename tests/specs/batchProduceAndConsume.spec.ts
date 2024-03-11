import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';
import {sortBy} from 'lodash-es';
import delay from 'delay';
import {getOffset} from '../services/getOffset.js';

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start(
            {
                KAFKA_BROKER: 'kafka:9092',
                MONITORING_SERVER_PORT: '3000',
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([
                    {topic: 'foo', targetPath: '/consumeFoo'},
                    {topic: 'bar', targetPath: '/consumeBar'},
                ]),
                TARGET_PROCESS_TYPE: 'batch',
            },
            ['foo', 'bar']
        );
    }, 5 * 60 * 1000);

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
    });

    it('batch produce and consume', async () => {
        const target1 = await mockHttpTarget(orchestrator.wiremockClient, '/consumeFoo', 200);
        const target2 = await mockHttpTarget(orchestrator.wiremockClient, '/consumeBar', 200);

        await produce(orchestrator, {
            topic: 'foo',
            messages: [
                {key: '1', value: JSON.stringify({data: 'foo1'})},
                {key: '2', value: JSON.stringify({data: 'foo2'})},
            ],
        });
        await produce(orchestrator, {
            topic: 'bar',
            messages: [
                {key: '1', value: JSON.stringify({data: 'bar1'})},
                {key: '2', value: JSON.stringify({data: 'bar2'})},
            ],
        });

        await delay(5000);

        await expect(
            getCalls(orchestrator.wiremockClient, target1).then((calls) => sortBy(calls, 'body.data'))
        ).resolves.toMatchSnapshot();

        await expect(
            getCalls(orchestrator.wiremockClient, target2).then((calls) => sortBy(calls, 'body.data'))
        ).resolves.toMatchSnapshot();

        await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(2);
        await expect(getOffset(orchestrator.kafkaClient, 'bar')).resolves.toBe(2);
    });
});
