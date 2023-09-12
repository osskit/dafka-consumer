import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';

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
            },
            ['foo']
        );
    }, 5 * 60 * 1000);

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
    });

    it('should add record headers to target call', async () => {
        const target = await mockHttpTarget(orchestrator.wiremockClient, '/consume', 200);

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

        await expect(getCalls(orchestrator.wiremockClient, target, true)).resolves.toMatchSnapshot();
    });
});
