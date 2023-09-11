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

    it('should add record headers to target call', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
            },
            ['foo']
        );
        const target = await mockHttpTarget(orchestrator.target, '/consume', 200);

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [
                {
                    value: JSON.stringify({data: 'foo'}),
                    headers: {
                        'x-request-id': '123',
                        'x-b3-traceid': '456',
                        'x-b3-spanid': '789',
                        'x-b3-parentspanid': '101112',
                        'x-b3-sampled': '1',
                        'x-b3-flags': '1',
                        'x-ot-span-context': 'foo',
                    },
                },
            ],
        });

        //assert
        await expect(getCalls(orchestrator.target, target, true)).resolves.toMatchSnapshot();
    });
});
