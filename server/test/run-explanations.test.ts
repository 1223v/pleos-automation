import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createApp } from '../src/http.js';
import { loadConfig, Config } from '../src/config.js';
import { BudgetLedger } from '../src/budget.js';
import { Planner, Provider } from '../src/planner.js';

const auth = 'test-only-not-a-real-token-0000000000';
const config = () => loadConfig({ SERVER_AUTH_TOKEN: auth, AI_ENABLED: 'true', OPENAI_API_KEY: 'fake', AI_DAILY_BUDGET_USD: '10', AI_MONTHLY_BUDGET_USD: '100' });
function request() {
  const workflow = JSON.parse(readFileSync('../contracts/fixtures/commute.json', 'utf8'));
  return { requestId: 'explanation-001', locale: 'ko-KR', workflow, startCause: 'SIMULATION', omittedEventCount: 0,
    run: { runId: 'run-001', workflowId: workflow.id, revision: 1, semanticHash: 'a'.repeat(64), simulation: true, status: 'COMPLETED', elapsedMillis: 0,
      events: [{ runId: 'run-001', nodeId: 'cold', status: 'PENDING', message: '관찰값 12°C · 기준 LTE 5 · FALSE', elapsedMillis: 0, iterations: [], branches: [], sequence: 1, observedValue: null }] } };
}
const answer = () => ({ runId: 'run-001', summary: '가상 온도 12°C에서 열선 조건을 충족하지 않았습니다.', details: [{ nodeId: 'cold', explanation: '5°C 이하 조건이 거짓입니다.', eventSequences: [1] }], limitations: ['시뮬레이션이며 실제 차량 결과가 아닙니다.'] });
const completion = (value: unknown) => ({ value, inputTokens: 100, outputTokens: 50 });
async function withServer(provider: Provider, fn: (post: (body: unknown, path?: string, token?: string) => Promise<Response>, ledger: BudgetLedger) => Promise<void>, c: Config = config()) {
  const ledger = new BudgetLedger(':memory:'); const app = createApp(c, ledger, provider);
  await new Promise<void>(resolve => app.listen(0, '127.0.0.1', resolve));
  const address = app.address(); assert.ok(address && typeof address !== 'string');
  const post = (body: unknown, path = '/v1/run-explanations', token = auth) => fetch(`http://127.0.0.1:${address.port}${path}`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  try { await fn(post, ledger); } finally { await new Promise<void>(resolve => app.close(() => resolve())); ledger.close(); }
}

test('explanation route is authenticated and disabled before any provider or reservation', async () => {
  let calls = 0;
  await withServer(async () => { calls++; return completion(answer()); }, async (post, ledger) => {
    assert.equal((await post(request(), undefined, 'wrong')).status, 401);
    const response = await post(request()); assert.equal(response.status, 503); assert.equal((await response.json()).error.code, 'AI_DISABLED');
    assert.equal(calls, 0); assert.equal(ledger.snapshot().dailyMicros, 0);
  }, loadConfig({ SERVER_AUTH_TOKEN: auth }));
});

test('explanation sends exact historic evidence and separate output contract, with shared budget', async () => {
  let input: unknown; let specification: any;
  await withServer(async (text, _config, ...extra: unknown[]) => { input = JSON.parse(text); specification = extra[0]; return completion(answer()); }, async (post, ledger) => {
    const body = request(), before = JSON.stringify(body); const response = await post(body);
    assert.equal(response.status, 200); assert.deepEqual(await response.json(), answer());
    assert.deepEqual(input, body); assert.equal(JSON.stringify(body), before);
    assert.equal(specification.name, 'run_explanation_v1'); assert.equal(specification.schema.additionalProperties, false);
    assert.match(specification.instructions, /untrusted/); assert.equal(ledger.snapshot().dailyMicros, 800);
    assert.equal((await post(body)).status, 409);
  });
});

test('explanation validates references, types, source separation and extra keys before provider', async () => {
  let calls = 0;
  await withServer(async () => { calls++; return completion(answer()); }, async (post, ledger) => {
    const cases: Array<(b: ReturnType<typeof request>) => void> = [
      b => { (b as any).approved = true; }, b => { b.run.revision = 2; }, b => { b.run.workflowId = 'other'; },
      b => { b.run.events[0]!.runId = 'other'; }, b => { b.run.events[0]!.nodeId = 'missing'; },
      b => { b.run.events[0]!.sequence = 0; }, b => { b.run.events.push({ ...b.run.events[0]! }); },
      b => { b.run.events[0]!.status = 'VERIFIED'; }, b => { b.startCause = 'MANUAL'; },
      b => { b.omittedEventCount = -1; }, b => { b.omittedEventCount = 1; }, b => { b.run.semanticHash = 'x'; },
      b => { (b.run.events[0] as any).approved = true; }, b => { b.run.elapsedMillis = -1; },
      b => { b.locale = 'bad_tag'; }, b => { (b as any).locale = 1; }, b => { b.run.events[0]!.iterations = [21] as never; },
      b => { b.run.events[0]!.branches = ['branch:8'] as never; }, b => { b.run.events[0]!.elapsedMillis = 1; },
    ];
    for (const mutate of cases) { const b = request(); mutate(b); const r = await post(b); assert.equal(r.status, 400, JSON.stringify(b)); }
    assert.equal(calls, 0); assert.equal(ledger.snapshot().dailyMicros, 0);
  });
});

test('explanation rejects hallucinated event references and command-bearing output', async () => {
  const bad: unknown[] = [
    { ...answer(), patch: {} }, { ...answer(), runId: 'other' },
    { ...answer(), details: [{ ...answer().details[0], eventSequences: [2] }] },
    { ...answer(), details: [{ ...answer().details[0], nodeId: 'navigation' }] },
    { ...answer(), details: [{ ...answer().details[0], nodeId: 'missing' }] },
    { ...answer(), summary: '' }, { ...answer(), details: [{ ...answer().details[0], eventSequences: [] }] },
    { ...answer(), details: [{ ...answer().details[0], eventSequences: [1, 1] }] },
  ];
  let index = 0;
  await withServer(async () => completion(bad[index++]), async (post) => {
    for (let i = 0; i < bad.length; i++) { const response = await post({ ...request(), requestId: `invalid-output-${i}` }); assert.equal(response.status, 502); assert.equal((await response.json()).error.code, 'INVALID_AI_OUTPUT'); }
  });
});

test('explanation accepts explicit omitted-event window without inventing omitted evidence', async () => {
  await withServer(async (input) => { const b = JSON.parse(input); assert.equal(b.omittedEventCount, 4); return completion({ ...answer(), limitations: [] }); }, async (post) => {
    const b = request(); b.run.events.push({ ...b.run.events[0]!, sequence: 6 }); b.omittedEventCount = 4;
    const response = await post(b); assert.equal(response.status, 200);
    const result = await response.json(); assert.ok(result.limitations.some((s: string) => s.includes('4')));
  });
});

test('explanation cannot report real VERIFIED as simulated or simulated effects as real', async () => {
  let calls = 0;
  await withServer(async () => { calls++; return completion(answer()); }, async (post) => {
    const b = request(); b.run.simulation = false; b.startCause = 'MANUAL'; b.run.events[0]!.status = 'SIMULATED';
    assert.equal((await post(b)).status, 400); assert.equal(calls, 0);
  });
});

test('explanations and drafts share one active-call gate in both directions', async () => {
  let release!: () => void; let entered!: () => void; let count = 0;
  let started = new Promise<void>(resolve => { entered = resolve; });
  let pending = new Promise<void>(resolve => { release = resolve; });
  await withServer(async (input) => { count++; entered(); await pending; return completion(JSON.parse(input).mode ? { status: 'needs_clarification', explanation: '목적지 필요', questions: ['목적지?'], workflow: null, patch: null } : answer()); }, async (post) => {
    const first = post(request()); await started;
    try { const denied = await post({ requestId: 'draft-request-001', mode: 'create', requestText: '출근' }, '/v1/routine-drafts'); assert.equal(denied.status, 429); assert.equal((await denied.json()).error.code, 'BUSY'); } finally { release(); }
    assert.equal((await first).status, 200);
    started = new Promise<void>(resolve => { entered = resolve; }); pending = new Promise<void>(resolve => { release = resolve; });
    const draft = post({ requestId: 'draft-request-002', mode: 'create', requestText: '출근' }, '/v1/routine-drafts'); await started;
    try { const denied = await post({ ...request(), requestId: 'explanation-002' }); assert.equal(denied.status, 429); assert.equal((await denied.json()).error.code, 'BUSY'); } finally { release(); }
    assert.equal((await draft).status, 200); assert.equal(count, 2);
  });
});

test('explanation reserves before calling and blocks insufficient budget', async () => {
  let calls = 0; const c = config(); c.dailyBudgetMicros = 1;
  await withServer(async () => { calls++; return completion(answer()); }, async (post, ledger) => {
    const r = await post(request()); assert.equal(r.status, 429); assert.equal((await r.json()).error.code, 'BUDGET_EXCEEDED');
    assert.equal(calls, 0); assert.equal(ledger.snapshot().dailyMicros, 0);
  }, c);
});

test('explanation provider failure retains uncertain reservation and sanitized error', async () => {
  await withServer(async () => { throw new Error('private upstream detail'); }, async (post, ledger) => {
    const r = await post(request()); assert.equal(r.status, 502); assert.doesNotMatch(await r.text(), /private upstream/);
    assert.equal(ledger.snapshot().unresolved, 1); assert.ok(ledger.snapshot().dailyMicros > 0);
    assert.equal((await post(request())).status, 409);
  });
});

test('explanation limits event count and request bytes before provider', async () => {
  let calls = 0;
  await withServer(async () => { calls++; return completion(answer()); }, async (post) => {
    const b = request(); b.run.events = Array.from({ length: 129 }, (_, i) => ({ ...b.run.events[0]!, sequence: i + 1 }));
    assert.equal((await post(b)).status, 400);
    b.run.events = [{ ...b.run.events[0]!, sequence: 1, message: 'x'.repeat(140000) }];
    assert.equal((await post(b)).status, 413); assert.equal(calls, 0);
  });
});

test('a blocked historic draft remains explainable even when its action is not executable', async () => {
  await withServer(async () => completion(answer()), async (post) => {
    const b = request(); const actionNode = b.workflow.nodes.find((n: any) => n.action?.type === 'NAVIGATE');
    actionNode.action.latitude = null; b.run.status = 'BLOCKED';
    const response = await post(b); assert.equal(response.status, 200); assert.equal((await response.json()).runId, 'run-001');
  });
});

test('official SDK explanation wire request uses supported strict schema and never exposes vehicle tools', async () => {
  const previous = globalThis.fetch; const ledger = new BudgetLedger(':memory:'); let calls = 0; let wireSchema: unknown;
  globalThis.fetch = (async (url: unknown, init: any) => {
    calls++; assert.equal(String(url), 'https://api.openai.com/v1/responses');
    const body = JSON.parse(init.body); assert.equal(body.store, false); assert.equal(body.text.format.name, 'run_explanation_v1');
    assert.equal(body.text.format.strict, true); assert.equal(body.tools, undefined); assert.match(body.instructions, /untrusted/);
    wireSchema = body.text.format.schema;
    assert.deepEqual(JSON.parse(body.input), request());
    return new Response(JSON.stringify({ id: 'resp-local-test', object: 'response', status: 'completed', output: [{ type: 'message', role: 'assistant', content: [{ type: 'output_text', text: JSON.stringify(answer()), annotations: [] }] }], usage: { input_tokens: 100, output_tokens: 50 } }), { headers: { 'Content-Type': 'application/json' } });
  }) as typeof fetch;
  try {
    assert.deepEqual(await new Planner(config(), ledger).explain(request()), answer()); assert.equal(calls, 1);
    // This keyword is not in the documented supported Structured Outputs subset.
    assert.doesNotMatch(JSON.stringify(wireSchema), /"uniqueItems"/);
  }
  finally { globalThis.fetch = previous; ledger.close(); }
});
