import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockFaultyHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';
import delay from 'delay';
import {topicRoutes} from '../services/topicRoutes.js';

describe('tests', () => {
    let orchestrator: Orchestrator;

    beforeEach(async () => {
        orchestrator = await start();
    }, 5 * 60 * 1000);

    afterEach(async () => {
        await orchestrator.stop();
    });

    it('consumer should wait after connection error from target', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF: '5,8000000,2',
                CONNECTION_FAILURE_RETRY_POLICY_MAX_RETRIES: '5',
            },
            ['foo']
        );
        const target = await mockFaultyHttpTarget(orchestrator.target, '/consume');

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });
        await delay(5000);

        //assert
        await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(5);
        await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
    });
});
