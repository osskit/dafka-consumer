import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget, mockFaultyHttpTarget} from '../services/target.js';
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

    it('recover from socket error', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                CONNECTION_FAILURE_RETRY_POLICY_EXPONENTIAL_BACKOFF: '50,50000,2',
                CONNECTION_FAILURE_RETRY_POLICY_MAX_DURATION_MS: '5000',
            },
            ['foo']
        );
        await mockFaultyHttpTarget(orchestrator.target, '/consume');

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });
        await delay(2000);
        await orchestrator.target.reset();
        const target = await mockHttpTarget(orchestrator.target, '/consume', 200);
        await delay(2000);

        //assert
        await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(1);
        await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
    });
});
