import delay from "delay";
import fetch from "node-fetch";
import Server from "simple-fake-server-server-client";
import { range } from "lodash";

import checkReadiness from "../checkReadiness";
import * as uuid from "uuid";

jest.setTimeout(180000);

const fakeHttpServer = new Server({
  baseUrl: `http://localhost`,
  port: 3000,
});

describe("tests", () => {
  beforeAll(async () => {
    // delay(120000);
    await expect(
      checkReadiness(["foo", "bar", "retry", "dead-letter", "unexpected"])
    ).resolves.toBeTruthy();
  });

  afterEach(async () => {
    await fakeHttpServer.clear();
  });

  it("liveliness", async () => {
    await delay(1000);
    const producer = await fetch("http://localhost:6000/isAlive");
    const consumer = await fetch("http://localhost:4001/isAlive");
    expect(producer.ok).toBeTruthy();
    expect(consumer.ok).toBeTruthy();
  });

  it("should produce and consume", async () => {
    const callId = await mockHttpTarget("/consume", 200);

    await produce("http://localhost:6000/produce", [
      {
        topic: "foo",
        key: "thekey",
        value: { data: "foo" },
        headers: { eventType: "test1", source: "test-service1" },
      },
    ]);
    await delay(1000);
    await produce("http://localhost:6000/produce", [
      {
        topic: "bar",
        key: "thekey",
        value: { data: "bar" },
        headers: { eventType: "test2", source: "test-service2" },
      },
    ]);
    await delay(1000);

    const { hasBeenMade, madeCalls } = await fakeHttpServer.getCall(callId);
    expect(hasBeenMade).toBeTruthy();
    expect(madeCalls.length).toBe(2);
    const actualHeaders1 = JSON.parse(madeCalls[0].headers["x-record-headers"]);
    const actualHeaders2 = JSON.parse(madeCalls[1].headers["x-record-headers"]);
    expect(madeCalls[0].headers["x-record-topic"]).toBe("foo");
    expect(actualHeaders1!.eventType).toEqual("test1");
    expect(actualHeaders1!.source).toEqual("test-service1");
    expect(madeCalls[1].headers["x-record-topic"]).toBe("bar");
    expect(actualHeaders2!.eventType).toEqual("test2");
    expect(actualHeaders2!.source).toEqual("test-service2");
  });

  it("should consume bursts of records", async () => {
    const callId = await mockHttpTarget("/consume", 200);

    const recordsCount = 1000;
    const records = range(recordsCount).map(() => ({
      topic: "foo",
      key: uuid(),
      value: { data: "foo" },
    }));

    await produce("http://localhost:6000/produce", records);
    await delay(recordsCount * 10);

    const { madeCalls } = await fakeHttpServer.getCall(callId);
    expect(madeCalls.length).toBe(recordsCount);
  });

  it("consumer should produce to dead letter topic when target response is 400", async () => {
    await mockHttpTarget("/consume", 400);
    const callId = await mockHttpTarget("/deadLetter", 200);

    await produce("http://localhost:6000/produce", [
      { topic: "foo", key: uuid(), value: { data: "foo" } },
    ]);
    await delay(5000);

    const { hasBeenMade, madeCalls } = await fakeHttpServer.getCall(callId);
    expect(hasBeenMade).toBeTruthy();
    expect(madeCalls[0].headers["x-record-original-topic"]).toEqual("foo");
  });

  it("consumer should produce to retry topic when target response is 500", async () => {
    await mockHttpTarget("/consume", 500);
    const callId = await mockHttpTarget("/retry", 200);

    await produce("http://localhost:6000/produce", [
      { topic: "foo", key: uuid(), value: { data: "foo" } },
    ]);
    await delay(5000);

    const { hasBeenMade, madeCalls } = await fakeHttpServer.getCall(callId);
    expect(hasBeenMade).toBeTruthy();
    expect(madeCalls[0].headers["x-record-original-topic"]).toEqual("foo");
  });

  it("consumer should terminate on an unexpected error", async () => {
    await delay(1000);
    let consumerLiveliness = await fetch("http://localhost:4002/isAlive");
    expect(consumerLiveliness.ok).toBeTruthy();

    await produce("http://localhost:6000/produce", [
      { topic: "unexpected", key: uuid(), value: { data: "unexpected" } },
    ]);
    await delay(10000);

    consumerLiveliness = await fetch("http://localhost:4002/isAlive");
    expect(consumerLiveliness.ok).toBeFalsy();
  });
});

const produce = (url: string, batch: any[]) =>
  fetch(url, {
    method: "post",
    body: JSON.stringify(batch),
    headers: { "Content-Type": "application/json" },
  });

const mockHttpTarget = (route: string, statusCode: number) =>
  fakeHttpServer.mock({
    method: "post",
    url: route,
    statusCode,
  });
