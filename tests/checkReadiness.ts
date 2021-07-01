// @ts-ignore
import {KafkaClient, Admin} from 'kafka-node';
import delay from 'delay';
import {promisify} from 'util';

const MAX_RETRIES = 30;
let admin: Admin;
let client: KafkaClient;
let retries = 0;

const connectToKafka = () => {
    if (admin != null) {
        return;
    }

    client = new KafkaClient({
        kafkaHost: `localhost:9092`,
        connectTimeout: 2000,
        requestTimeout: 2000,
        connectRetryOptions: {
            retries: MAX_RETRIES,
            minTimeout: 1 * 1000,
            maxTimeout: 2 * 1000,
        },
    });
    admin = new Admin(client);
};

const checkReadiness = async (expectedTopics: string[]): Promise<boolean> => {
    if (retries == MAX_RETRIES) {
        if (client) {
            client.close();
        }
        return false;
    }
    retries++;
    await delay(2000);
    try {
        connectToKafka();
        const promisifiedListTopics = promisify(admin.listTopics.bind(admin));

        const res = await promisifiedListTopics();

        const metadata = res?.[1]?.metadata;

        if (!metadata) return checkReadiness(expectedTopics);

        if (expectedTopics.every((topic) => Object.keys(metadata).includes(topic))) {
            client.close();
            return true;
        }

        return checkReadiness(expectedTopics);
    } catch (e) {
        return checkReadiness(expectedTopics);
    }
};

export default checkReadiness;
