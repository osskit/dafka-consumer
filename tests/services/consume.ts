import {Kafka, KafkaMessage} from 'kafkajs';

export const consume = async (kafka: Kafka, topic: string, parse = true) => {
    const consumer = kafka.consumer({groupId: 'orchestrator'});
    await consumer.subscribe({topic: topic, fromBeginning: true});
    const consumedMessage = await new Promise<KafkaMessage>((resolve) => {
        consumer.run({
            eachMessage: async ({message}) => resolve(message),
        });
    });
    await consumer.disconnect();
    const value = parse ? JSON.parse(consumedMessage.value?.toString() ?? '{}') : consumedMessage.value?.toString();
    const headers = Object.fromEntries(
        Object.entries(consumedMessage.headers!).map(([key, value]) => [key, value?.toString()])
    );
    return {value, headers};
};
