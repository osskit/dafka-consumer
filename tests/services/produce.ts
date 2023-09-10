import {ProducerRecord} from 'kafkajs';
import {Orchestrator} from '../testcontainers/orchestrator.js';

export const produce = (orchestrator: Orchestrator, record: ProducerRecord) =>
    orchestrator.producer().then((producer) => producer.send(record));
