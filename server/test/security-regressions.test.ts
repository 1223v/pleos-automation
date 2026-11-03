import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { loadConfig } from '../src/config.js';
import { BudgetLedger } from '../src/budget.js';
import { Planner, openAIProvider } from '../src/planner.js';
import { Connectors } from '../src/connectors.js';
import { validateWorkflow } from '../src/contracts.js';
import { createApp } from '../src/http.js';

const env = { SERVER_AUTH_TOKEN: 'test-only-not-a-real-token-0000000000' };
const config = () => loadConfig({ ...env, AI_ENABLED: 'true', OPENAI_API_KEY: 'fake', AI_DAILY_BUDGET_USD: '10', AI_MONTHLY_BUDGET_USD: '100' });
const request = { requestId: 'security-001', mode: 'create', requestText: '출근 루틴', locale: 'ko-KR' };
const answer = { status: 'needs_clarification', explanation: '목적지 필요', questions: ['목적지?'], workflow: null, patch: null };
const fixture = () => JSON.parse(readFileSync('../contracts/fixtures/commute.json', 'utf8'));
function tempDb(fn: (path: string) => void) {
  const root = resolve('.data/tests'); mkdirSync(root, { recursive: true });
  const dir = mkdtempSync(join(root, 'security-'));
  try { fn(join(dir, 'ledger.sqlite')); } finally { assert.ok(dir.startsWith(root)); rmSync(dir, { recursive: true, force: true }); }
}

test('known overspend blocks later reservations across restart and preserves known charge', () => tempDb(path => {
  const clock = () => new Date('2026-10-29T03:00:00Z');
  const ledger = new BudgetLedger(path, clock); ledger.reserve('one', 90, 100, 1000);
  assert.throws(() => ledger.settle('one', 40, 0));
  assert.equal(ledger.snapshot().dailyMicros, 120); ledger.close();
  const restored = new BudgetLedger(path, clock);
  try { assert.throws(() => restored.reserve('two', 1, 1000, 10000)); } finally { restored.close(); }
}));
test('settled costs cannot be settled again to refund spend', () => {
  const ledger = new BudgetLedger(':memory:'); ledger.reserve('one', 100, 1000, 1000); ledger.settle('one', 20, 0);
  try { assert.throws(() => ledger.settle('one', 0, 0)); assert.equal(ledger.snapshot().dailyMicros, 60); } finally { ledger.close(); }
});
test('budget periods change at Korean midnight including month boundary', () => {
  let now = new Date('2026-10-31T14:59:59.999Z'); const ledger = new BudgetLedger(':memory:', () => now);
  try {
    ledger.reserve('last', 90, 100, 100); assert.equal(ledger.snapshot().day, '2026-10-31');
    now = new Date('2026-10-31T15:00:00Z');
    assert.equal(ledger.snapshot().day, '2026-11-01'); assert.equal(ledger.snapshot().monthlyMicros, 0);
    ledger.reserve('next', 100, 100, 100); assert.equal(ledger.snapshot().dailyMicros, 100);
  } finally { ledger.close(); }
});
test('legacy UTC-day charges conservatively overlap both Korean dates and months', () => tempDb(path => {
  const db = new DatabaseSync(path);
  db.exec("CREATE TABLE requests(request_id TEXT PRIMARY KEY,day TEXT NOT NULL,month TEXT NOT NULL,charged_micros INTEGER NOT NULL CHECK(charged_micros>=0),state TEXT NOT NULL CHECK(state IN ('RESERVED','SETTLED','UNCERTAIN'))) STRICT; INSERT INTO requests VALUES('old','2026-10-31','2026-10',90,'UNCERTAIN');"); db.close();
  let now = new Date('2026-10-31T03:00:00Z'); const ledger = new BudgetLedger(path, () => now);
  try { assert.equal(ledger.snapshot().dailyMicros, 90); now = new Date('2026-10-31T16:00:00Z'); assert.equal(ledger.snapshot().day, '2026-11-01'); assert.equal(ledger.snapshot().monthlyMicros, 90); assert.equal(ledger.snapshot().dailyMicros, 90); } finally { ledger.close(); }
}));
test('two ledger connections cannot reserve beyond one shared limit', () => tempDb(path => {
  const first = new BudgetLedger(path), second = new BudgetLedger(path);
  try { first.reserve('one', 70, 100, 100); assert.throws(() => second.reserve('two', 40, 100, 100)); assert.equal(second.snapshot().dailyMicros, 70); } finally { first.close(); second.close(); }
}));
test('malformed mode locale and create-only fields are rejected before provider or reservation', async () => {
  for (const change of [{ mode: ['create'] }, { locale: {} }, { locale: '' }, { baseWorkflow: { secret: 'unnecessary' } }, { baseRevision: 1 }]) {
    const ledger = new BudgetLedger(':memory:'); let calls = 0;
    const planner = new Planner(config(), ledger, async () => { calls++; return { value: answer, inputTokens: 1, outputTokens: 1 }; });
    try { await assert.rejects(planner.plan({ ...request, ...change })); assert.equal(calls, 0); assert.equal(ledger.snapshot().dailyMicros, 0); } finally { ledger.close(); }
  }
});
test('complete valid graph rejects approval and semantic malformed values', () => {
  for (const mutate of [
    (w: any) => { w.approved = true; },
    (w: any) => { w.nodes.find((n: any) => n.kind === 'ACTION').action = null; },
    (w: any) => { w.edges[0].port = 'invalid'; },
    (w: any) => { w.trigger.zoneId = 'Not/AZone'; },
    (w: any) => { w.nodes.find((n: any) => n.id === 'navigation').action.latitude = 91; },
    (w: any) => { w.nodes.find((n: any) => n.id === 'heat').action.value = -1; },
  ]) { const w = fixture(); mutate(w); assert.throws(() => validateWorkflow(w)); }
  assert.equal(validateWorkflow(fixture()).revision, 1);
});
test('unavailable HA climate cannot write even when its old target matches', async () => {
  let calls = 0;
  const f = (async () => { calls++; return new Response(JSON.stringify({ entity_id: 'climate.room', state: 'unavailable', attributes: { min_temp: 10, max_temp: 30, temperature: 23 } })); }) as typeof fetch;
  const c = loadConfig({ ...env, CONNECTORS_ENABLED: 'true', HA_BASE_URL: 'https://ha.test', HA_ACCESS_TOKEN: 'fake', HA_ALLOWED_ENTITIES: 'climate.room' });
  await assert.rejects(new Connectors(c, f).execute({ action: { type: 'HA_CALL', targetId: 'climate.room', text: 'climate.set_temperature', value: 23 } }));
  assert.equal(calls, 1);
});
test('HA unavailable after write stays UNKNOWN and is never retried', async () => {
  let calls = 0; const f = (async () => { calls++; return new Response(JSON.stringify(calls === 2 ? [] : { entity_id: 'climate.room', state: calls === 1 ? 'heat' : 'unknown', attributes: { min_temp: 10, max_temp: 30, temperature: 23 } })); }) as typeof fetch;
  const c = loadConfig({ ...env, CONNECTORS_ENABLED: 'true', HA_BASE_URL: 'https://ha.test', HA_ACCESS_TOKEN: 'fake', HA_ALLOWED_ENTITIES: 'climate.room' });
  const actual = await new Connectors(c, f).execute({ action: { type: 'HA_CALL', targetId: 'climate.room', text: 'climate.set_temperature', value: 23 } });
  assert.equal(actual.result.status, 'UNKNOWN'); assert.equal(calls, 3);
});
test('actual SDK pins official origin and sends strict nonstored nonretry request', async () => {
  const oldFetch = globalThis.fetch, oldOrigin = process.env.OPENAI_BASE_URL;
  process.env.OPENAI_BASE_URL = 'http://synthetic.invalid/v1'; let calls = 0;
  globalThis.fetch = (async (url: any, init: any) => {
    calls++; assert.ok(String(url).startsWith('https://api.openai.com/v1/'));
    const body = JSON.parse(init.body); assert.equal(body.store, false); assert.equal(body.text.format.strict, true); assert.equal(body.model, 'gpt-6.1-sol');
    return new Response(JSON.stringify({ id: 'resp-test', object: 'response', status: 'completed', output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: JSON.stringify(answer), annotations: [] }] }], usage: { input_tokens: 1, output_tokens: 2 } }), { headers: { 'Content-Type': 'application/json' } });
  }) as typeof fetch;
  try { assert.equal((await openAIProvider('test', config())).outputTokens, 2); assert.equal(calls, 1); }
  finally { globalThis.fetch = oldFetch; if (oldOrigin === undefined) delete process.env.OPENAI_BASE_URL; else process.env.OPENAI_BASE_URL = oldOrigin; }
});
test('HTTP rejects browser and oversized final response, keeping error bounded', async () => {
  const c = loadConfig({ ...env, CONNECTORS_ENABLED: 'true', GOOGLE_CLIENT_ID: 'fake', GOOGLE_CLIENT_SECRET: 'fake', GOOGLE_REFRESH_TOKEN: 'fake' });
  let calls = 0; const fetcher = (async () => new Response(JSON.stringify(++calls === 1 ? { access_token: 'fake' } : { items: [{ id: 'large-event', summary: '"'.repeat(80000), start: { date: '2026-10-31' }, end: { date: '2026-11-01' } }] }))) as typeof fetch;
  const ledger = new BudgetLedger(':memory:'); const server = createApp(c, ledger, undefined, fetcher);
  await new Promise<void>(r => server.listen(0, '127.0.0.1', r)); const address = server.address(); assert.ok(address && typeof address !== 'string');
  const url = `http://127.0.0.1:${address.port}`, headers = { Authorization: 'Bearer ' + env.SERVER_AUTH_TOKEN, 'Content-Type': 'application/json' };
  try {
    assert.equal((await fetch(url + '/v1/status', { headers: { ...headers, Origin: 'https://other.test' } })).status, 403);
    const response = await fetch(url + '/v1/connector-actions', { method: 'POST', headers, body: JSON.stringify({ action: { type: 'CALENDAR_READ' } }) });
    assert.equal(response.status, 502); const body = await response.text(); assert.ok(body.length < 1000); assert.equal(JSON.parse(body).error.code, 'TOO_LARGE');
  } finally { await new Promise<void>(r => server.close(() => r())); ledger.close(); }
});

test('invalid usage keeps reservation and locks the ledger', () => {
  for(const input of [-1, NaN, Infinity, 0.5]) {
    const ledger=new BudgetLedger(':memory:');
    try { ledger.reserve('one',100,1000,1000);assert.throws(()=>ledger.settle('one',input,0));assert.equal(ledger.snapshot().dailyMicros,100);assert.throws(()=>ledger.reserve('two',1,1000,1000)); }
    finally { ledger.close(); }
  }
});
test('real SDK does not retry network errors', async () => {
  const previous=globalThis.fetch;let calls=0;
  globalThis.fetch=(async()=>{calls++;throw new Error('synthetic offline');}) as typeof fetch;
  try { await assert.rejects(openAIProvider('test',config()));assert.equal(calls,1); } finally {globalThis.fetch=previous;}
});
test('HTTP MIME JSON body and request rate boundaries are enforced', async () => {
  const ledger=new BudgetLedger(':memory:');const server=createApp(loadConfig(env),ledger);
  await new Promise<void>(r=>server.listen(0,'127.0.0.1',r));const a=server.address();assert.ok(a&&typeof a!=='string');
  const url=`http://127.0.0.1:${a.port}`;const headers={Authorization:'Bearer '+env.SERVER_AUTH_TOKEN,'Content-Type':'application/json'};
  try {
    assert.equal((await fetch(url+'/v1/routine-drafts',{method:'POST',headers:{...headers,'Content-Type':'application/json-invalid'},body:'{}'})).status,415);
    assert.equal((await fetch(url+'/v1/routine-drafts',{method:'POST',headers,body:'{'})).status,400);
    assert.equal((await fetch(url+'/v1/routine-drafts',{method:'POST',headers,body:' '.repeat(131073)})).status,413);
    for(let i=3;i<60;i++)assert.equal((await fetch(url+'/v1/status',{headers})).status,200);
    assert.equal((await fetch(url+'/v1/status',{headers})).status,429);
  } finally { await new Promise<void>(r=>server.close(()=>r()));ledger.close(); }
});
test('connector stream overflow and redirect failure return sanitized errors', async () => {
  const c=loadConfig({...env,CONNECTORS_ENABLED:'true',KAKAO_REST_API_KEY:'fake'});
  let calls=0;
  const f=(async(_url:any,init:any)=> {calls++;assert.equal(init.redirect,'error');assert.ok(init.signal);return new Response('x'.repeat(262145));}) as typeof fetch;
  await assert.rejects(new Connectors(c,f).execute({action:{type:'PLACE_SEARCH',text:'place'}}),/크기/);assert.equal(calls,1);
});
