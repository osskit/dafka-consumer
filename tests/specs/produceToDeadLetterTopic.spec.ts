import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
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

    it('consumer should produce to dead letter topic', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                DEAD_LETTER_TOPIC: 'dead',
                PRODUCE_TO_DEAD_LETTER_TOPIC_WHEN_STATUS_CODE_MATCH: '428',
            },
            ['foo', 'dead']
        );
        const target = await mockHttpTarget(orchestrator.target, '/consume', 428);

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });
        await delay(5000);

        //assert
        await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(1);
        await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
        await expect(orchestrator.consume('dead')).resolves.toMatchSnapshot();
    });
});
