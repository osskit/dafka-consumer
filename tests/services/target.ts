import {HttpMethod, RequestPattern, WireMockClient} from '@osskit/wiremock-client';
import {omit} from 'lodash-es';

export const mockHttpTarget = (wiremock: WireMockClient, url: string, status: number) =>
    wiremock.createMapping({
        request: {
            url: url,
            method: HttpMethod.Post,
        },
        response: {
            status,
        },
    });

export const mockFaultyHttpTarget = (wiremock: WireMockClient, url: string) =>
    wiremock.createMapping({
        request: {
            url: url,
            method: HttpMethod.Post,
        },
        response: {
            fault: 'CONNECTION_RESET_BY_PEER',
        },
    });

export const getCalls = (wiremock: WireMockClient, target: RequestPattern, withHeaders = false) =>
    wiremock.waitForCalls(target).then((calls) =>
        calls.map(({body, headers}) => ({
            body,
            ...(withHeaders ? {headers: omit(headers, 'x-record-timestamp')} : {}),
        }))
    );
