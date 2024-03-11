import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {mockHttpTarget} from '../services/target.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';
import delay from 'delay';
import {consumeAll} from '../services/consume.js';

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
                TARGET_PROCESS_TYPE: 'batch',
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

    it('batch produce to dead letter topic', async () => {
        await mockHttpTarget(orchestrator.wiremockClient, '/consume', 428, 'foo foo bar bar');

        await produce(orchestrator, {
            topic: 'foo',
            messages: [
                {key: '1', value: JSON.stringify({data: 'foo1'})},
                {key: '2', value: JSON.stringify({data: 'foo2'})},
                {key: '3', value: JSON.stringify({data: 'foo3'})},
                {key: '4', value: JSON.stringify({data: 'foo4'})},
                {key: '5', value: JSON.stringify({data: 'foo5'})},
            ],
        });

        await delay(5000);

        await expect(consumeAll(orchestrator.kafkaClient, 'dead', 5)).resolves.toMatchSnapshot();
    });
});
