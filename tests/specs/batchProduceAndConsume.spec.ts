import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';
import delay from 'delay';
import {getOffset} from '../services/getOffset.js';
import {sortArray} from '../services/sortArray.js';

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
                COMMIT_INTERVAL_MS: '100',
                TARGET_PROCESS_TYPE: 'batch',
                BATCH_PARALLELISM_FACTOR: '1',
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
                {key: '3', value: JSON.stringify({data: 'foo3'})},
                {key: '4', value: JSON.stringify({data: 'foo4'})},
                {key: '5', value: JSON.stringify({data: 'foo5'})},
                {key: '6', value: JSON.stringify({data: 'foo6'})},
                {key: '7', value: JSON.stringify({data: 'foo7'})},
                {key: '8', value: JSON.stringify({data: 'foo8'})},
                {key: '9', value: JSON.stringify({data: 'foo9'})},
                {key: '10', value: JSON.stringify({data: 'foo10'})},
            ],
        });
        await produce(orchestrator, {
            topic: 'bar',
            messages: [
                {key: '1', value: JSON.stringify({data: 'bar1'})},
                {key: '2', value: JSON.stringify({data: 'bar2'})},
                {key: '3', value: JSON.stringify({data: 'bar3'})},
                {key: '4', value: JSON.stringify({data: 'bar4'})},
                {key: '5', value: JSON.stringify({data: 'bar5'})},
                {key: '6', value: JSON.stringify({data: 'bar6'})},
                {key: '7', value: JSON.stringify({data: 'bar7'})},
                {key: '8', value: JSON.stringify({data: 'bar8'})},
                {key: '9', value: JSON.stringify({data: 'bar9'})},
                {key: '10', value: JSON.stringify({data: 'bar10'})},
            ],
        });

        await delay(10000);

        await expect(
            getCalls(orchestrator.wiremockClient, target1).then((calls) => sortArray(calls))
        ).resolves.toMatchSnapshot();

        await expect(
            getCalls(orchestrator.wiremockClient, target2).then((calls) => sortArray(calls))
        ).resolves.toMatchSnapshot();

        await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(10);
        await expect(getOffset(orchestrator.kafkaClient, 'bar')).resolves.toBe(10);
    });
});
