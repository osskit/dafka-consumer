import {StartedNetwork, Wait} from 'testcontainers';
import {KafkaContainer} from '@testcontainers/kafka';
import {Kafka, logLevel} from 'kafkajs';
import delay from 'delay';
import fs from 'node:fs';

export const kafka = async (network: StartedNetwork, topics: string[]) => {
    const container = await new KafkaContainer('confluentinc/cp-kafka:7.2.2')
        .withNetwork(network)
        .withNetworkAliases('kafka')
        .withWaitStrategy(Wait.forLogMessage('started (kafka.server.KafkaServer)'))
        .start();

    await delay(10000);

    if (process.env.DEBUG) {
        try {
            fs.truncateSync('kafka.log', 0);
        } catch (err) {
            fs.writeFileSync('kafka.log', '', {flag: 'wx'});
        }
        await container.logs().then((logs) => logs.pipe(fs.createWriteStream('kafka.log')));
    }

    const client = new Kafka({
        logLevel: logLevel.NOTHING,
        brokers: [`${container.getHost()}:${container.getMappedPort(9093)}`],
    });

    await client.admin().createTopics({topics: topics.map((topic) => ({topic}))});

    return {
        stop: () => container.stop(),
        client,
    };
};
