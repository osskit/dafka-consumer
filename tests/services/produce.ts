import {ProducerRecord} from 'kafkajs';
import {Orchestrator} from '../testcontainers/orchestrator.js';

export const produce = async (orchestrator: Orchestrator, record: ProducerRecord) => {
    const producer = orchestrator.kafkaClient.producer();
    await producer.connect();
    await producer.send(record);
};
