import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {produce} from '../services/produce.js';
import delay from 'delay';
import {mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
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
                BODY_HEADERS_PATHS: 'bla',
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

    it('consumer should produce to dead letter topic when value is not valid JSON', async () => {
        await mockHttpTarget(orchestrator.wiremockClient, '/consume', 200);

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: 'wat?'}],
        });
        await delay(5000);

        //assert
        await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(1);
        await expect(consume(orchestrator.kafkaClient, 'dead', false)).resolves.toMatchSnapshot();
    });
});
