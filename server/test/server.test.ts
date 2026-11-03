import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { loadConfig } from '../src/config.js';
import { BudgetLedger } from '../src/budget.js';
import { Planner } from '../src/planner.js';
import { createApp } from '../src/http.js';
import { Connectors } from '../src/connectors.js';
import { validateWorkflow, validateResponse } from '../src/contracts.js';

const baseEnv = { SERVER_AUTH_TOKEN: 'test-only-not-a-real-token-0000000000' };
const enabled = () => loadConfig({ ...baseEnv, AI_ENABLED: 'true', OPENAI_API_KEY: 'fake', AI_DAILY_BUDGET_USD: '10', AI_MONTHLY_BUDGET_USD: '100' });
const request = { requestId: 'test-request-001', mode: 'create', requestText: '출근 루틴', locale: 'ko-KR' };
const clarification = { status: 'needs_clarification', explanation: '회사 주소가 필요합니다.', questions: ['회사 목적지를 알려주세요.'], workflow: null, patch: null };

test('default and missing budgets block before provider creation/call', async () => {
  for (const c of [loadConfig(baseEnv), loadConfig({ ...baseEnv, AI_ENABLED: 'true', OPENAI_API_KEY: 'fake' })]) {
    let calls = 0; const ledger = new BudgetLedger(':memory:'); const p = new Planner(c, ledger, async () => { calls++; throw new Error(); });
    await assert.rejects(p.plan(request), /보류/); assert.equal(calls, 0); assert.equal(ledger.snapshot().dailyMicros, 0); ledger.close();
  }
});
test('mock completion validates and duplicate request does not bill twice', async () => {
  let calls = 0; const ledger = new BudgetLedger(':memory:'); const p = new Planner(enabled(), ledger, async () => { calls++; return { value: clarification, inputTokens: 100, outputTokens: 50 }; });
  assert.equal((await p.plan(request)).status, 'needs_clarification'); assert.equal(ledger.snapshot().dailyMicros, 800);
  await assert.rejects(p.plan(request), /이미 접수/); assert.equal(calls, 1); ledger.close();
});
test('uncertain reservations survive restart and enforce budget', () => {
  const testRoot = resolve('.data/tests'); mkdirSync(testRoot, { recursive: true });
  const folder = mkdtempSync(join(testRoot, 'pleos-budget-')); const path = join(folder, 'ledger.sqlite');
  try { const first = new BudgetLedger(path); first.reserve('one', 90, 100, 1000); first.markUncertain('one'); first.close();
    const second = new BudgetLedger(path); assert.equal(second.snapshot().dailyMicros, 90); assert.throws(() => second.reserve('two', 20, 100, 1000), /예산/); second.close();
  } finally { assert.ok(resolve(folder).startsWith(testRoot + '\\') || resolve(folder).startsWith(testRoot + '/')); rmSync(folder, { recursive: true, force: true }); }
});
test('provider failure keeps full reservation and hides provider secrets', async () => {
  const ledger = new BudgetLedger(':memory:'); const p = new Planner(enabled(), ledger, async () => { throw new Error('secret-key'); });
  await assert.rejects(p.plan(request), e => e instanceof Error && !e.message.includes('secret-key'));
  assert.equal(ledger.snapshot().unresolved, 1); assert.ok(ledger.snapshot().dailyMicros > 0); ledger.close();
});
test('invalid AI output is never a draft', async () => {
  const ledger = new BudgetLedger(':memory:'); const p = new Planner(enabled(), ledger, async () => ({ value: { execute: true }, inputTokens: 10, outputTokens: 10 }));
  await assert.rejects(p.plan(request), /형식/); ledger.close();
});
test('connectors disabled and unlisted HA target produce no fetch', async () => {
  let calls = 0; const f = (async () => { calls++; throw new Error(); }) as typeof fetch;
  await assert.rejects(new Connectors(loadConfig(baseEnv), f).execute({ action: { type: 'HA_CALL' } }), /꺼져/);
  const c = loadConfig({ ...baseEnv, CONNECTORS_ENABLED: 'true', HA_BASE_URL: 'https://example.test', HA_ACCESS_TOKEN: 'fake', HA_ALLOWED_ENTITIES: 'light.allowed' });
  await assert.rejects(new Connectors(c, f).execute({ action: { type: 'HA_CALL', targetId: 'light.other', text: 'light.turn_on' } }), /허용된/);
  assert.equal(calls, 0);
});
test('HA acknowledgement without matching state remains UNKNOWN', async () => {
  const methods: string[] = []; const f = (async (_url, init) => { methods.push(init?.method ?? 'GET'); return new Response(JSON.stringify(init?.method === 'POST' ? [] : { entity_id: 'light.allowed', state: 'off', attributes: {} }), { status: 200 }); }) as typeof fetch;
  const c = loadConfig({ ...baseEnv, CONNECTORS_ENABLED: 'true', HA_BASE_URL: 'https://example.test', HA_ACCESS_TOKEN: 'fake', HA_ALLOWED_ENTITIES: 'light.allowed' });
  assert.equal((await new Connectors(c, f).execute({ action: { type: 'HA_CALL', targetId: 'light.allowed', text: 'light.turn_on' } })).result.status, 'UNKNOWN');
  assert.equal(methods.filter(m => m === 'POST').length, 1);
});
test('HTTP authentication and disabled AI do not call a provider', async () => {
  const ledger = new BudgetLedger(':memory:'); let calls = 0;
  const server = createApp(loadConfig(baseEnv), ledger, async () => { calls++; throw new Error(); });
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const address = server.address(); assert.ok(address && typeof address !== 'string'); const url = `http://127.0.0.1:${address.port}`;
  try {
    assert.equal((await fetch(url + '/v1/status')).status, 401);
    const headers = { Authorization: 'Bearer ' + baseEnv.SERVER_AUTH_TOKEN, 'Content-Type': 'application/json' };
    assert.equal((await fetch(url + '/v1/status', { headers })).status, 200);
    assert.equal((await fetch(url + '/v1/routine-drafts', { method: 'POST', headers, body: JSON.stringify(request) })).status, 503);
    assert.equal(calls, 0);
  } finally { await new Promise<void>(resolve => server.close(() => resolve())); ledger.close(); }
});
test('schema rejects unexpected execution approval', () => { assert.throws(() => validateWorkflow({ approved: true }), /スキーマ|스키마/); });
test('shared Kotlin fixture and minimal edit preserve other nodes', () => {
  const w=validateWorkflow(JSON.parse(readFileSync('../contracts/fixtures/commute.json','utf8')));
  const nodes=w.nodes as Record<string,unknown>[];
  const original=nodes.find(n=>n.id==='cold')!;
  const node={...original,condition:{...(original.condition as Record<string,unknown>),value:8}};
  const answer={status:'draft',explanation:'열선 기준을 8도로 변경합니다.',questions:[],workflow:null,patch:{baseRevision:1,operations:[{type:'REPLACE_NODE',node,nodeId:'cold',edge:null,edgeId:null,title:null,trigger:null}]}};
  assert.equal(validateResponse(answer,'edit',w).status,'draft');
  assert.equal((original.condition as Record<string,unknown>).value,5);
  assert.throws(()=>validateResponse({...answer,patch:{...answer.patch,baseRevision:2}},'edit',w),/버전/);
});
