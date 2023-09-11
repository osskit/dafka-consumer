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
        await orchestrator.stop();
    });

    it('consumer should produce to retry topic', async () => {
        //prepare
        await orchestrator.consumer(
            {
                GROUP_ID: 'test',
                TARGET_BASE_URL: 'http://mocks:8080',
                TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
                RETRY_TOPIC: 'retry',
                RETRY_PROCESS_WHEN_STATUS_CODE_MATCH: '511',
                PRODUCE_TO_RETRY_TOPIC_WHEN_STATUS_CODE_MATCH: '511',
                RETRY_POLICY_EXPONENTIAL_BACKOFF: '50,500,10',
                RETRY_POLICY_MAX_DURATION: '1000',
            },
            ['foo', 'retry']
        );
        const target = await mockHttpTarget(orchestrator.target, '/consume', 511);

        //act
        await produce(orchestrator, {
            topic: 'foo',
            messages: [{value: JSON.stringify({data: 'foo'})}],
        });
        await delay(5000);

        //assert
        await expect(getCalls(orchestrator.target, target)).resolves.toHaveLength(10);
        await expect(getOffset(orchestrator.admin(), 'foo')).resolves.toBe(1);
        await expect(orchestrator.consume('retry')).resolves.toMatchSnapshot();
    });
});
