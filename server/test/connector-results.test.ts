import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadConfig } from '../src/config.js';
import { Connectors } from '../src/connectors.js';
import { AppError } from '../src/errors.js';

const calendar = loadConfig({ CONNECTORS_ENABLED: 'true', GOOGLE_CLIENT_ID: 'fake', GOOGLE_CLIENT_SECRET: 'fake', GOOGLE_REFRESH_TOKEN: 'fake' });
const kakao = loadConfig({ CONNECTORS_ENABLED: 'true', KAKAO_REST_API_KEY: 'fake' });
const w3w = loadConfig({ CONNECTORS_ENABLED: 'true', WHAT3WORDS_ENABLED: 'true', WHAT3WORDS_API_KEY: 'fake' });
const ha = loadConfig({ CONNECTORS_ENABLED: 'true', HA_BASE_URL: 'https://ha.test', HA_ACCESS_TOKEN: 'fake', HA_ALLOWED_ENTITIES: 'light.allowed' });
const malformedOutput = (e: unknown) => e instanceof AppError && e.code === 'INVALID_CONNECTOR_OUTPUT' && e.status === 502;
const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } });
function calendarFetcher(events: unknown): typeof fetch {
  return (async url => String(url) === 'https://oauth2.googleapis.com/token' ? json({ access_token: 'fake', token_type: 'Bearer', expires_in: 3600 }) : json(events)) as typeof fetch;
}

test('HA light unknown or unavailable state blocks before any service write', async () => {
  for (const state of ['unknown', 'unavailable']) {
    const methods: string[] = [];
    const fetcher = (async (_url, init) => { methods.push(init?.method ?? 'GET'); return json({ entity_id: 'light.allowed', state, attributes: {} }); }) as typeof fetch;
    await assert.rejects(new Connectors(ha, fetcher).execute({ action: { type: 'HA_CALL', targetId: 'light.allowed', text: 'light.turn_on' } }), e => e instanceof AppError && e.code === 'NOT_AVAILABLE');
    assert.deepEqual(methods, ['GET']);
  }
});

test('HA light mismatched or missing current state blocks before service write', async () => {
  for (const current of [{ entity_id: 'light.other', state: 'off' }, { entity_id: 'light.allowed' }]) {
    const methods: string[] = [];
    const fetcher = (async (_url, init) => { methods.push(init?.method ?? 'GET'); return json(current); }) as typeof fetch;
    await assert.rejects(new Connectors(ha, fetcher).execute({ action: { type: 'HA_CALL', targetId: 'light.allowed', text: 'light.turn_off' } }), e => e instanceof AppError && e.code === 'NOT_AVAILABLE');
    assert.deepEqual(methods, ['GET']);
  }
});

test('HA light reads before write and verifies the selected on or off state afterwards', async () => {
  for (const [service, before, after] of [['turn_on', 'off', 'on'], ['turn_off', 'on', 'off']]) {
    const methods: string[] = [];
    const fetcher = (async (_url, init) => {
      methods.push(init?.method ?? 'GET');
      if (init?.method === 'POST') { assert.deepEqual(JSON.parse(String(init.body)), { entity_id: 'light.allowed' }); return json([]); }
      return json({ entity_id: 'light.allowed', state: methods.length === 1 ? before : after, attributes: {} });
    }) as typeof fetch;
    const answer = await new Connectors(ha, fetcher).execute({ action: { type: 'HA_CALL', targetId: 'light.allowed', text: `light.${service}` } });
    assert.equal(answer.result.status, 'VERIFIED');
    assert.equal(answer.result.observedValue, JSON.stringify(after));
    assert.deepEqual(methods, ['GET', 'POST', 'GET']);
  }
});

test('Calendar rejects malformed event collections and explicit error payloads', async () => {
  for (const response of [{ items: 'not-a-list' }, { items: null }, { items: [null] }, { items: [[]] }, { items: [{}] }, { items: [{ id: 2 }] }, { items: [{ id: 'event', summary: 2 }] }, { items: [{ id: 'event', start: 'not-an-object' }] }, { items: [{ id: 'event', end: { dateTime: 7 } }] }, { error: { message: 'synthetic-private-error' } }]) {
    await assert.rejects(new Connectors(calendar, calendarFetcher(response)).execute({ action: { type: 'CALENDAR_READ' } }), malformedOutput);
  }
});

test('Calendar preserves omitted or empty items and valid optional event fields', async () => {
  const events = [
    { id: 'timed', start: { dateTime: '2026-10-31T09:00:00+09:00' }, end: { dateTime: '2026-10-31T10:00:00+09:00' } },
    { id: 'all-day', summary: '', start: { date: '2026-10-31' }, end: { date: '2026-11-01' } },
    { id: 'cancelled-sparse' },
  ];
  for (const [response, expected] of [[{}, []], [{ items: [] }, []], [{ items: events }, events]]) {
    const answer = await new Connectors(calendar, calendarFetcher(response)).execute({ action: { type: 'CALENDAR_READ' } });
    assert.equal(answer.result.status, 'VERIFIED');
    assert.deepEqual(JSON.parse(answer.result.observedValue!), expected);
  }
});

test('Kakao rejects missing or malformed place lists and unusable destination coordinates', async () => {
  const place = { id: 'place-1', place_name: '시험 장소', x: '127.0', y: '37.5' };
  const badPlaces = [null, [], {}, { ...place, id: null }, { ...place, place_name: 9 }, { ...place, x: '' }, { ...place, x: 'not-a-coordinate' }, { ...place, x: '181' }, { ...place, y: '91' }];
  for (const response of [{}, { documents: null }, { documents: 'not-a-list' }, ...badPlaces.map(p => ({ documents: [p] }))]) {
    await assert.rejects(new Connectors(kakao, (async () => json(response)) as typeof fetch).execute({ action: { type: 'PLACE_SEARCH', text: '시험 장소' } }), malformedOutput);
  }
});

test('Kakao preserves empty results and valid place coordinates without optional metadata', async () => {
  for (const documents of [[], [{ id: 'place-1', place_name: '시험 장소', x: '0', y: '0' }], [{ id: 'edge', place_name: '경계', x: '-180', y: '90', phone: '', road_address_name: '' }]]) {
    const answer = await new Connectors(kakao, (async () => json({ documents })) as typeof fetch).execute({ action: { type: 'PLACE_SEARCH', text: '시험 장소' } });
    assert.equal(answer.result.status, 'VERIFIED');
    assert.deepEqual(JSON.parse(answer.result.observedValue!), documents);
  }
});

test('what3words rejects absent malformed or out of range coordinates', async () => {
  for (const response of [{}, { coordinates: null }, { coordinates: [] }, { coordinates: {} }, { coordinates: { lat: '37', lng: 127 } }, { coordinates: { lat: 91, lng: 127 } }, { coordinates: { lat: 37, lng: -181 } }, { error: { code: 'BadWords', message: 'synthetic-private-error' } }]) {
    await assert.rejects(new Connectors(w3w, (async () => json(response)) as typeof fetch).execute({ action: { type: 'WHAT3WORDS_RESOLVE', text: 'a.b.c' } }), malformedOutput);
  }
});

test('what3words verifies numeric coordinates including zero and range boundaries', async () => {
  for (const coordinates of [{ lat: 0, lng: 0 }, { lat: -90, lng: 180 }, { lat: 90, lng: -180 }]) {
    const answer = await new Connectors(w3w, (async () => json({ coordinates })) as typeof fetch).execute({ action: { type: 'WHAT3WORDS_RESOLVE', text: 'a.b.c' } });
    assert.equal(answer.result.status, 'VERIFIED');
    assert.deepEqual(JSON.parse(answer.result.observedValue!), coordinates);
  }
});
