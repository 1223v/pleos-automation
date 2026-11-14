import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { BudgetLedger } from '../src/budget.js';
import { loadConfig } from '../src/config.js';
import { object, type ObjectValue, validateResponse, validateWorkflow } from '../src/contracts.js';
import { Planner } from '../src/planner.js';
import { createApp } from '../src/http.js';

const base = JSON.parse(readFileSync('../contracts/fixtures/winter-camping.json', 'utf8'));
const config = loadConfig({ AI_ENABLED: 'true', OPENAI_API_KEY: 'mock-only', AI_DAILY_BUDGET_USD: '10', AI_MONTHLY_BUDGET_USD: '10' });
const create = { requestId: 'winter-create-01', mode: 'create',
  requestText: '플레오스, 겨울 차박 루틴을 만들고 싶어. 바깥이 5도 이하일 때만 공조를 23도로 맞춰줘. 매트리스를 놓게 의자를 젖히고 조명은 살짝 어둡게 해줘.' };
const light = base.nodes.find((n: ObjectValue) => n.id === 'light30');
const seat = base.nodes.find((n: ObjectValue) => n.id === 'seat');
const edit = { requestId: 'winter-edit-01', mode: 'edit', requestText: '처음엔 그대로 두고 10분 뒤에 20%로 낮춰줘',
  baseWorkflow: base, baseRevision: base.revision, selectedNodeId: light.id };
const unresolved = { ...seat, label: '어느 좌석인가요?', action: { ...seat.action, area: '' } };
const preview = { ...base, nodes: base.nodes.map((n: ObjectValue) => n.id === seat.id ? unresolved : n) };
const asking = { status: 'needs_clarification', explanation: '좌석을 확인해 주세요.', questions: ['어느 좌석인가요?'], workflow: preview, patch: null };
const context = { requestText: create.requestText, questions: asking.questions, answers: ['앞줄 왼쪽'] };
const wait = { ...light, id: 'wait', label: '10분 대기', kind: 'WAIT', durationSeconds: 600, action: null };
const darker = { ...light, id: 'darker', action: { ...light.action, value: 20 } };
function op(type: string, fields: ObjectValue) {
  return { type, node: null, nodeId: null, edge: null, edgeId: null, title: null, trigger: null, ...fields };
}
function line(id: string, from: string, to: string) {
  return op('CONNECT', { edge: { id, source: from, target: to, port: 'next' } });
}
const splice = [op('DISCONNECT', { edgeId: 'light30-end' }), op('ADD_NODE', { node: wait }), op('ADD_NODE', { node: darker }),
  line('p1',light.id,'wait'), line('p2','wait','darker'), line('p3','darker','end')];
function proposal(operations: ObjectValue[]) {
  return { status: 'draft', explanation: '조명 변경', questions: [], workflow: null, patch: { baseRevision: 1, operations } };
}
async function plan(request: ObjectValue, response: ObjectValue, checkInput?: (input: ObjectValue) => void) {
  const ledger = new BudgetLedger(':memory:');
  const planner = new Planner(config, ledger, async (input) => {
    checkInput?.(object(JSON.parse(input)));
    return { value: response, inputTokens: 20, outputTokens: 10 };
  });
  try { return await planner.plan(request); } finally { ledger.close(); }
}
test('create clarification carries a typed preview whose unresolved seat cannot be a draft', async () => {
  assert.deepEqual(await plan(create, asking), asking);
  assert.throws(() => validateWorkflow(preview), /영역/);
  assert.throws(() => validateResponse({ ...asking, status: 'draft' }, 'create'), /영역/);
});
test('clarification preview must still pass graph bounds, topology, action validation and have a real question', () => {
  assert.throws(() => validateResponse({ ...asking, workflow: { ...preview, edges: [] } }, 'create'), /노드|연결/);
  assert.throws(() => validateResponse({ ...asking, workflow: { ...base, nodes: base.nodes.map((n: ObjectValue) => n.id === light.id ? { ...light, action: { ...light.action, area: '' } } : n) } }, 'create'), /영역/);
  for (const questions of [[], ['   ']]) assert.throws(() => validateResponse({ ...asking, questions, workflow: null }, 'create'), /질문/);
  for (const status of ['needs_clarification','unsupported']) {
    assert.throws(() => validateResponse({ ...asking, status, patch: proposal(splice).patch }, 'create'));
    assert.throws(() => validateResponse({ ...asking, status }, 'edit', base));
  }
});
test('winter camping HTTP flow carries the first request, seat answer and selected edit across three calls', async () => {
  const fullDraft = { status: 'draft', explanation: '앞줄 왼쪽 등받이를 선택했어요.', questions: [], workflow: base, patch: null };
  const ledger = new BudgetLedger(':memory:');
  const mockConfig = { ...config, authToken: 'winter-test-only'.padEnd(32, '-') };
  const server = createApp(mockConfig, ledger, async encoded => {
    const input = object(JSON.parse(encoded));
    if (input.clarification) {
      assert.deepEqual(input.clarification, context);
      assert.equal(input.requestText, create.requestText);
    }
    const value = input.mode === 'edit' ? proposal(splice) : input.clarification ? fullDraft : asking;
    return { value, inputTokens: 20, outputTokens: 10 };
  });
  await new Promise<void>(done => server.listen(0, '127.0.0.1', done));
  try {
    const address = server.address();
    assert.ok(address && typeof address !== 'string');
    const url = new URL('/v1/routine-drafts', 'http://127.0.0.1:' + address.port);
    async function request(body: ObjectValue) {
      const response = await fetch(url, { method: 'POST', headers: { Authorization: 'Bearer ' + mockConfig.authToken,
        'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      assert.equal(response.status, 200);
      return object(await response.json());
    }
    const first = await request(create);
    assert.deepEqual(first, asking);
    const second = await request({ ...create, requestId: 'winter-answer-01', clarification: context });
    assert.deepEqual(second.workflow, base);
    const edges = object(second.workflow).edges as ObjectValue[];
    assert.deepEqual(edges.filter(e => e.source === 'cold').map(e => [e.port, e.target]), [['true','hvac23'],['false','seat']]);
    const third = await request({ ...edit, baseWorkflow: second.workflow });
    assert.deepEqual(third, proposal(splice));
  } finally {
    await new Promise<void>(done => server.close(() => done()));
    ledger.close();
  }
});
test('a selected followup passes the original edit, paired answers and selection to the mock provider', async () => {
  const response = proposal([op('REPLACE_NODE', { nodeId: light.id, node: { ...light, action: { ...light.action, value: 20 } } })]);
  const editContext = { requestText: edit.requestText, questions: ['몇 %인가요?'], answers: ['20%'] };
  const request = { ...edit, clarification: editContext };
  const result = await plan(request, response, input => {
    assert.deepEqual(input.clarification, editContext);
    assert.equal(input.selectedNodeId, light.id);
    assert.equal(input.requestText, request.requestText);
  });
  assert.deepEqual(result, response);
});
test('invalid selection or clarification context is rejected before the provider', async () => {
  for (const change of [{ selectedNodeId: 'missing' }, { selectedNodeId: null }]) {
    await assert.rejects(plan({ ...edit, ...change }, asking, () => assert.fail('provider was called')), /선택/);
  }
  for (const invalid of [{ ...context, answers: [] }, { ...context, questions: ['   '] }, { ...context, answers: ['a'.repeat(1001)] },
    { ...context, requestText: '다른 요청' }, { ...context, extra: 1 }, { ...context, questions: Array(6).fill('좌석?'), answers: Array(6).fill('운전석') }]) {
    await assert.rejects(plan({ ...create, clarification: invalid }, asking, () => assert.fail('provider was called')), /보충/);
  }
  await assert.rejects(plan({ ...create, selectedNodeId: light.id }, asking), /선택/);
});
test('selected brightness may insert a wait and same-area dimming before its original successor', async () => {
  assert.deepEqual(await plan(edit, proposal(splice)), proposal(splice));
});
test('a selected edit cannot change title, trigger, another node, identity, branch or unrelated edge even transiently', async () => {
  const other = base.nodes.find((n: ObjectValue) => n.id === 'cold');
  const invalid = [
    [op('CHANGE_TITLE', { title: 'new' })], [op('CHANGE_TRIGGER', { trigger: base.trigger })],
    [op('REPLACE_NODE', { nodeId: 'cold', node: { ...other, label: '다른 카드' } })],
    [op('REPLACE_NODE', { nodeId: light.id, node: { ...light, action: { ...light.action, area: 'ROW_1_RIGHT' } } })],
    [op('REPLACE_NODE', { nodeId: light.id, node: { ...light, action: { ...light.action, type: 'SET_HVAC_TARGET' } } })],
    [op('REMOVE_NODE', { nodeId: light.id })],
    [...splice, op('DISCONNECT', { edgeId: 'cold-seat' }), op('CONNECT', { edge: base.edges.find((e: ObjectValue) => e.id === 'cold-seat') })],
    [...splice.slice(0,5), line('p3','darker','cold')],
    [splice[0]!, splice[1]!, op('ADD_NODE', { node: { ...darker, action: { ...darker.action, type: 'SET_HVAC_TARGET' } } }), ...splice.slice(3)],
    [splice[0]!, op('ADD_NODE', { node: { ...wait, action: light.action } }), ...splice.slice(2)],
    [...splice.slice(0,3), line('cold-seat',light.id,'wait'), ...splice.slice(4)],
    [...splice, op('ADD_NODE', { node: wait })], [...splice, op('ADD_NODE', { node: { ...wait, id: 'unused' } })],
    [...splice.slice(0,4), line('p2','wait','wait'), splice[5]!],
  ];
  for (const ops of invalid) await assert.rejects(plan(edit, proposal(ops)), /선택/);
});
test('the disconnected edge ID may be reused on the new path and stale selected edits are rejected', async () => {
  const reuse = [...splice.slice(0,3), line('light30-end',light.id,'wait'), ...splice.slice(4)];
  assert.deepEqual(await plan(edit, proposal(reuse)), proposal(reuse));
  const stale = { ...proposal(splice), patch: { baseRevision: 2, operations: splice } };
  await assert.rejects(plan(edit, stale), /버전/);
});

test('the planner receives only reported area IDs and can present a seat question even when the backrest has one zone', async () => {
  const vehicleAreas = [
    { type: 'SET_AMBIENT_BRIGHTNESS', areas: [light.action.area] },
    { type: 'MOVE_SEAT_LIMITED', axis: 'BACKREST', areas: ['ROW_1_LEFT'] },
    { type: 'MOVE_SEAT_LIMITED', axis: 'FORE_AFT', areas: ['ROW_1_RIGHT'] },
  ];
  assert.deepEqual(await plan({ ...create, vehicleAreas }, asking, input => assert.deepEqual(input.vehicleAreas, vehicleAreas)), asking);
  const draft = { ...asking, status: 'draft', questions: [], workflow: base };
  assert.deepEqual(await plan({ ...create, clarification: context, vehicleAreas }, draft), draft);
  for (const hint of [{ type: 'SET_AMBIENT_BRIGHTNESS', areas: ['ROW_1_LEFT'] },
    { type: 'MOVE_SEAT_LIMITED', axis: 'BACKREST', areas: [] }]) {
    await assert.rejects(plan({ ...create, vehicleAreas: [hint] }, draft), /영역/);
  }
});

test('area hints reject state, invalid identifiers, excessive data and duplicate capability entries before an AI call', async () => {
  const entry = { type: 'SET_HVAC_TARGET', areas: ['ROW_1_LEFT'] };
  const invalid = [null, {}, Array(7).fill(entry), [entry,entry], [{ ...entry, currentTemperature: 4 }],
    [{ ...entry, type: 'OPEN_APP' }], [{ ...entry, axis: 'FORE_AFT' }], [{ ...entry, type: 'MOVE_SEAT_LIMITED' }],
    [{ ...entry, type: 'MOVE_SEAT_LIMITED', axis: 'ANGLE' }], [{ ...entry, areas: Array(33).fill('A') }],
    [{ ...entry, areas: ['ROW_1_LEFT','ROW_1_LEFT'] }], [{ ...entry, areas: ['FRONT; drop'] }],
    [{ ...entry, areas: ['A'.repeat(161)] }], [{ ...entry, areas: ['DRIVER'] }]];
  for (const vehicleAreas of invalid) {
    await assert.rejects(plan({ ...edit, vehicleAreas }, proposal(splice), () => assert.fail('provider was called')), /영역/);
  }
});

test('an existing unrelated legacy area does not block a selected edit when current hints differ', async () => {
  const old = { ...base, nodes: base.nodes.map((node: ObjectValue) => node.id === seat.id ?
    { ...seat, action: { ...seat.action, area: 'DRIVER' } } : node) };
  const hints = [{ type: 'MOVE_SEAT_LIMITED', axis: 'BACKREST', areas: ['ROW_1_LEFT'] }];
  assert.deepEqual(await plan({ ...edit, baseWorkflow: old, vehicleAreas: hints }, proposal(splice)), proposal(splice));
  const wrongLight = [{ type: 'SET_AMBIENT_BRIGHTNESS', areas: ['ROW_1_LEFT'] }];
  await assert.rejects(plan({ ...edit, vehicleAreas: wrongLight }, proposal(splice)), /영역/);
});
