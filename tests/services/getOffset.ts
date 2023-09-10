import {Admin} from 'kafkajs';

export const getOffset = async (admin: Admin, topic: string) => {
    await admin.connect();
    const metadata = await admin.fetchOffsets({groupId: 'test', topics: [topic]});
    admin.disconnect();
    return Number.parseInt(metadata[0]?.partitions[0]?.offset!);
};
