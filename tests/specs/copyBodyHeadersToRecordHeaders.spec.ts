import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start();
    }, 5 * 60 * 1000);

    afterEach(async () => {
        await orchestrator.stop();
    });

    it('copy body headers to record headers', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                BODY_HEADERS_PATHS: 'bla,baz',
            },
            ['foo']
        );
        const target = await mockHttpTarget(orchestrator.target, '/consume', 200);

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [
                {
                    value: JSON.stringify({data: 'foo', bla: {foo: 'bar', baz: null}}),
                },
            ],
        });

        //assert
        await expect(getCalls(orchestrator.target, target, true)).resolves.toMatchSnapshot();
    });
});
