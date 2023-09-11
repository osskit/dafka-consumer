import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';

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

    it('should produce and consume with regex patterns', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: '^([^.]+).foo', targetPath: '/consume'}]),
            },
            ['prefix.foo']
        );
        const target = await mockHttpTarget(orchestrator.target, '/consume', 200);

        //act
        await produce(orchestrator, {
            topic: 'prefix.foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });

        //assert
        await expect(getCalls(orchestrator.target, target)).resolves.toMatchSnapshot();
        await expect(getOffset(orchestrator.admin(), 'prefix.foo')).resolves.toBe(1);
    });
});
