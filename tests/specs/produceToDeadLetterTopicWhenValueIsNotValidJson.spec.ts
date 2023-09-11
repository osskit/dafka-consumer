import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {produce} from '../services/produce.js';
import delay from 'delay';

const topicRoutes = (topicRoutes: {topic: string; targetPath: string}[]) =>
    topicRoutes.map(({topic, targetPath}) => `${topic}:${targetPath}`).join(',');

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start();
    }, 5 * 60 * 1000);

    afterEach(async () => {
        orchestrator.stop();
    });

    it('consumer should produce to dead letter topic when value is not valid JSON', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                DEAD_LETTER_TOPIC: 'dead',
            },
            ['foo', 'dead']
        );

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: 'wat'}],
        });
        await delay(5000);

        //assert
        // await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(-1);
        await expect(orchestrator.consume('dead')).resolves.toMatchSnapshot();
    });
});
