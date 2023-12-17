import {Kafka} from 'kafkajs';
import pWaitFor from 'p-wait-for';

export const getOffset = (kafka: Kafka, topic: string, expectedOffset: number) =>
    pWaitFor(
        async () => {
            const admin = kafka.admin();
            await admin.connect();
            const metadata = await admin.fetchOffsets({groupId: 'test', topics: [topic]});
            admin.disconnect();
            return Number.parseInt(metadata[0]?.partitions[0]?.offset!) === expectedOffset;
        },
        {interval: 100, timeout: 5000}
    );
