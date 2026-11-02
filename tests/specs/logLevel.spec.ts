import type {Orchestrator} from '../testcontainers/orchestrator.js';
import {start} from '../testcontainers/orchestrator.js';
import {getCalls, mockHttpTarget} from '../services/target.js';
import {getOffset} from '../services/getOffset.js';
import {produce} from '../services/produce.js';
import {topicRoutes} from '../services/topicRoutes.js';
import delay from 'delay';

const startConsumer = (env: Record<string, string>) =>
    start(
        {
            KAFKA_BROKER: 'kafka:9092',
            MONITORING_SERVER_PORT: '3000',
            GROUP_ID: 'test',
            TARGET_BASE_URL: 'http://mocks:8080',
            TOPICS_ROUTES: topicRoutes([{topic: 'foo', targetPath: '/consume'}]),
            ...env,
        },
        ['foo']
    );

const consumeOne = async (orchestrator: Orchestrator) => {
    const target = await mockHttpTarget(orchestrator.wiremockClient, '/consume', 200);

    await produce(orchestrator, {
        topic: 'foo',
        messages: [{key: '1', value: JSON.stringify({data: 'foo1'})}],
    });

    await delay(30000);

    await expect(getCalls(orchestrator.wiremockClient, target)).resolves.toHaveLength(1);
    await expect(getOffset(orchestrator.kafkaClient, 'foo')).resolves.toBe(1);

    return orchestrator.dafkaConsumerLogs();
};

describe('tests', () => {
    let orchestrator: Orchestrator | undefined;

    afterEach(async () => {
        if (!orchestrator) {
            return;
        }
        await orchestrator.stop();
        orchestrator = undefined;
    });

    it('should log per-record lines by default', async () => {
        orchestrator = await startConsumer({});

        const logs = await consumeOne(orchestrator);

        expect(logs).toContain('"message":"process message started"');
        expect(logs).toContain('"message":"message acknowledged"');
        expect(logs).toContain('"message":"consumer was assigned to partitions"');
    });

    it('should drop per-record lines when LOG_LEVEL is info', async () => {
        orchestrator = await startConsumer({LOG_LEVEL: 'info'});

        const logs = await consumeOne(orchestrator);

        expect(logs).not.toContain('"message":"process message started"');
        expect(logs).not.toContain('"message":"process message completed"');
        expect(logs).not.toContain('"message":"message acknowledged"');
        expect(logs).not.toContain('"message":"batch process started"');
        expect(logs).toContain('"message":"consumer was assigned to partitions"');
    });
});
