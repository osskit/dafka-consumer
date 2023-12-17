import {Kafka} from 'kafkajs';

export const getOffset = async (kafka: Kafka, topic: string) => {
    const admin = kafka.admin();
    await admin.connect();
    const metadata = await admin.fetchOffsets({groupId: 'test', topics: [topic]});
    admin.disconnect();
    return Number.parseInt(metadata[0]?.partitions[0]?.offset!);
};
