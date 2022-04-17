import delay from 'delay';
import fetch from 'node-fetch';
import Server from 'simple-fake-server-server-client';
import {range} from 'lodash';

import checkReadiness from '../checkReadiness';
import * as uuid from 'uuid';

jest.setTimeout(180000);

const fakeHttpServer = new Server({
    baseUrl: `http://localhost`,
    port: 3000,
});

describe('tests', () => {
    beforeAll(async () => {
        await expect(
            checkReadiness(['foo', 'bar', 'lol.bar', 'retry', 'dead-letter', 'unexpected'])
        ).resolves.toBeTruthy();
    });

    afterEach(async () => {
        await fakeHttpServer.clear();
    });

    it('readiness', async () => {
        await delay(1000);
        const producer = await fetch('http://localhost:6000/ready');
        const consumer = await fetch('http://localhost:4001/ready');
        expect(producer.ok).toBeTruthy();
        expect(consumer.ok).toBeTruthy();
    });

    it('should produce and consume', async () => {
        const callId = await mockHttpTarget('/consume', 200);

        await produce('http://localhost:6000/produce', [
            {
                topic: 'foo',
                key: 'thekey',
                value: {data: 'foo'},
            },
        ]);
        await delay(1000);

        const {hasBeenMade, madeCalls} = await fakeHttpServer.getCall(callId);
        expect(hasBeenMade).toBeTruthy();
        expect(madeCalls.length).toBe(1);
        expect(madeCalls[0]).toMatchSnapshot({
            headers: {'x-record-timestamp': expect.any(String), 'x-record-offset': expect.any(String)},
        });
    });

    it('should produce and consume with regex patterns', async () => {
        const callId = await mockHttpTarget('/consume', 200);

        await produce('http://localhost:6000/produce', [
            {
                topic: 'lol.bar',
                key: 'thekey',
                value: {data: 'foo'},
            },
        ]);
        await delay(1000);
        await produce('http://localhost:6000/produce', [
            {
                topic: 'bar',
                key: 'thekey',
                value: {data: 'bar'},
            },
        ]);
        await delay(1000);

        const {hasBeenMade, madeCalls} = await fakeHttpServer.getCall(callId);
        expect(hasBeenMade).toBeTruthy();
        expect(madeCalls.length).toBe(2);
    });

    it('should add tracing headers', async () => {
        const callId = await mockHttpTarget('/consume', 200);

        await produce(
            'http://localhost:6000/produce',
            [
                {
                    topic: 'foo',
                    key: 'thekey',
                    value: {data: 'foo'},
                },
            ],
            {
                'x-request-id': '123',
                'x-b3-traceid': '456',
                'x-b3-spanid': '789',
                'x-b3-parentspanid': '101112',
                'x-b3-sampled': '1',
                'x-b3-flags': '1',
                'x-ot-span-context': 'foo',
            }
        );
        await delay(1000);

        const {hasBeenMade, madeCalls} = await fakeHttpServer.getCall(callId);
        expect(hasBeenMade).toBeTruthy();
        expect(madeCalls[0]).toMatchSnapshot({
            headers: {'x-record-timestamp': expect.any(String), 'x-record-offset': expect.any(String)},
        });
    });

    it('should consume bursts of records', async () => {
        const callId = await mockHttpTarget('/consume', 200);

        const recordsCount = 1000;
        const records = range(recordsCount).map(() => ({
            topic: 'foo',
            key: uuid(),
            value: {data: 'foo'},
        }));

        await produce('http://localhost:6000/produce', records);
        await delay(recordsCount * 10);

        const {madeCalls} = await fakeHttpServer.getCall(callId);
        expect(madeCalls.length).toBe(recordsCount);
    });

    it('consumer should produce to dead letter topic when target response is 400', async () => {
        await mockHttpTarget('/consume', 400);
        const callId = await mockHttpTarget('/deadLetter', 200);

        await produce('http://localhost:6000/produce', [{topic: 'foo', key: uuid(), value: {data: 'foo'}}]);
        await delay(5000);

        const {hasBeenMade, madeCalls} = await fakeHttpServer.getCall(callId);
        expect(hasBeenMade).toBeTruthy();
        expect(madeCalls[0].headers['x-record-original-topic']).toEqual('foo');
    });

    it('consumer should produce to retry topic when target response is 500', async () => {
        await mockHttpTarget('/consume', 500);
        const callId = await mockHttpTarget('/retry', 200);

        await produce('http://localhost:6000/produce', [{topic: 'foo', key: uuid(), value: {data: 'foo'}}]);
        await delay(5000);

        const {hasBeenMade, madeCalls} = await fakeHttpServer.getCall(callId);
        expect(hasBeenMade).toBeTruthy();
        expect(madeCalls[0].headers['x-record-original-topic']).toEqual('foo');
    });

    it('consumer should terminate on an unexpected error', async () => {
        await delay(1000);
        const consumerReadiness = await fetch('http://localhost:4002/ready');
        expect(consumerReadiness.ok).toBeTruthy();

        await produce('http://localhost:6000/produce', [
            {
                topic: 'unexpected',
                key: uuid(),
                value: {data: 'unexpected'},
            },
        ]);
        await delay(10000);

        const consumerLiveness = await fetch('http://localhost:4002/alive');
        expect(consumerLiveness.ok).toBeFalsy();
    });
});

const produce = (url: string, batch: any[], headers?: object) =>
    fetch(url, {
        method: 'post',
        body: JSON.stringify(batch),
        headers: {'Content-Type': 'application/json', ...headers},
    });

const mockHttpTarget = (route: string, statusCode: number) =>
    fakeHttpServer.mock({
        method: 'post',
        url: route,
        statusCode,
    });
