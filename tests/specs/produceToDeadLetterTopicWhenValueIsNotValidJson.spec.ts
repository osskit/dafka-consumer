import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {produce} from '../services/produce.js';
import delay from 'delay';
import {mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {topicRoutes} from '../services/topicRoutes.js';

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start();
    }, 5 * 60 * 1000);

    afterEach(async () => {
        await orchestrator.stop();
    });

    it('consumer should produce to dead letter topic when value is not valid JSON', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                DEAD_LETTER_TOPIC: 'dead',
                BODY_HEADERS_PATHS: 'bla',
            },
            ['foo', 'dead']
        );
        await mockHttpTarget(orchestrator.target, '/consume', 200);

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: 'wat?'}],
        });
        await delay(5000);

        //assert
        await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
        await expect(orchestrator.consume('dead', false)).resolves.toMatchSnapshot();
    });
});
