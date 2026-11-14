import { readFileSync } from 'node:fs';
import { Ajv } from 'ajv';
import { AppError } from './errors.js';
import { validateSelectedOperations } from './selected-edit.js';
import { checkAreaHints, type VehicleAreas } from './planner-areas.js';

const root = new URL('../../../contracts/', import.meta.url);
export const workflowSchema = JSON.parse(readFileSync(new URL('workflow.schema.json', root), 'utf8')) as Record<string, unknown>;
export const responseSchema = JSON.parse(readFileSync(new URL('planner-response.schema.json', root), 'utf8')) as Record<string, unknown>;
export const catalogue = JSON.parse(readFileSync(new URL('connector-catalog.json', root), 'utf8')) as unknown;
const ajv = new Ajv({ strict: true, allErrors: true });
const workflowCheck = ajv.compile(workflowSchema);
const responseCheck = ajv.compile(responseSchema);
export type ObjectValue = Record<string, unknown>;
export function object(value: unknown): ObjectValue {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new AppError('INVALID_INPUT', 'JSON 객체가 필요합니다.');
  return value as ObjectValue;
}
function invalid(message: string): never { throw new AppError('INVALID_GRAPH', message); }
function finite(v: unknown): v is number { return typeof v === 'number' && Number.isFinite(v); }
function between(v: unknown, low: number, high: number): boolean { return finite(v) && v >= low && v <= high; }
function text(v: unknown): v is string { return typeof v === 'string' && v.trim().length > 0; }
function condition(value: unknown): void {
  const c = object(value);
  switch (c.field) {
    case 'OUTSIDE_TEMPERATURE': case 'SPEED': if (!finite(c.value)) invalid('조건 비교값이 필요합니다.'); break;
    case 'GEAR': if (!['P', 'R', 'N', 'D'].includes(String(c.text)) || !['EQ', 'NE'].includes(String(c.operator))) invalid('기어 조건 오류'); break;
    case 'SEAT_OCCUPIED': if (typeof c.flag !== 'boolean' || !['EQ', 'NE'].includes(String(c.operator))) invalid('점유 조건 오류'); break;
    case 'TIME_WINDOW': if (!between(c.startMinute, 0, 1439) || !between(c.endMinute, 1, 1440) || Number(c.startMinute) >= Number(c.endMinute)) invalid('시간 구간 오류'); break;
    case 'WEEKDAY': if (!(c.weekdays as number[]).length || (c.weekdays as number[]).some(v => v < 1 || v > 7)) invalid('요일 오류'); break;
  }
}
function action(value: unknown, preview: boolean): void {
  const a = object(value);
  const unresolvedSeat = preview && a.type === 'MOVE_SEAT_LIMITED' && a.area === '';
  if ((!text(a.area) || a.area.length > 160) && !unresolvedSeat || a.value !== null && !finite(a.value)) invalid('동작 영역·값 오류');
  switch (a.type) {
    case 'NAVIGATE': if (!between(a.latitude, -90, 90) || !between(a.longitude, -180, 180) || !text(a.text)) invalid('목적지 오류'); break;
    case 'SET_HVAC_TARGET': if (!finite(a.value)) invalid('목표 온도 오류'); break;
    case 'SET_AMBIENT_BRIGHTNESS': if (!between(a.value, 0, 100)) invalid('밝기 범위 오류'); break;
    case 'SET_AMBIENT_COLOR': if (typeof a.text !== 'string' || !/^#FF[0-9a-fA-F]{6}$/.test(a.text)) invalid('ARGB 오류'); break;
    case 'SET_SEAT_HEAT': if (!finite(a.value) || !Number.isInteger(a.value) || a.value < 0) invalid('열선 단계 오류'); break;
    case 'MOVE_SEAT_LIMITED': if (a.axis === null || a.direction === null) invalid('시트 축·방향 오류'); break;
    case 'PLAY_LOCAL_AUDIO': if (a.targetId !== 'rest-tone') invalid('음원 오류'); break;
    case 'OPEN_APP': case 'MEDIA_CONTROL': case 'HA_CALL': if (!text(a.targetId)) invalid('대상 필요'); break;
    case 'PLACE_SEARCH': case 'WHAT3WORDS_RESOLVE': if (!text(a.text)) invalid('검색어 필요'); break;
  }
}
/** Schema shape only, used when explaining a rejected historical draft, never to authorize execution. */
export function validateWorkflowShape(value: unknown): ObjectValue {
  if (!workflowCheck(value)) throw new AppError('INVALID_WORKFLOW', '워크플로우가 스키마 v2와 일치하지 않습니다.');
  return object(value);
}
export function validateWorkflow(value: unknown): ObjectValue {
  return checkWorkflow(value, false);
}
function checkWorkflow(value: unknown, preview: boolean): ObjectValue {
  const w = validateWorkflowShape(value), nodes = w.nodes as ObjectValue[], edges = w.edges as ObjectValue[];
  if (!text(w.id) || !text(w.title) || w.title.length > 100) invalid('루틴 이름·ID 오류');
  const trigger = object(w.trigger);
  try { new Intl.DateTimeFormat('ko-KR', { timeZone: String(trigger.zoneId) }); } catch { invalid('시간대 오류'); }
  (trigger.filters as unknown[]).forEach(condition);
  if (trigger.type === 'CONDITION_ENTER') condition(trigger.condition);
  if (trigger.type === 'TIME' && (!between(trigger.minuteOfDay, 0, 1439) || !(trigger.weekdays as number[]).length || (trigger.weekdays as number[]).some(v => v < 1 || v > 7))) invalid('트리거 시각·요일 오류');
  if (nodes.length < 2 || nodes.filter(n => n.kind === 'START').length !== 1 || !nodes.some(n => n.kind === 'END')) throw new AppError('INVALID_GRAPH', '시작·종료 노드를 확인하세요.');
  const ids = new Set(nodes.map(n => n.id));
  if (ids.size !== nodes.length || new Set(edges.map(e => e.id)).size !== edges.length) throw new AppError('INVALID_GRAPH', 'ID가 중복되었습니다.');
  for (const edge of edges) if (!ids.has(edge.source) || !ids.has(edge.target)) throw new AppError('INVALID_GRAPH', '연결 대상이 없습니다.');
  const active = new Set<unknown>(), visited = new Set<unknown>();
  function visit(id: unknown): void {
    if (active.has(id)) throw new AppError('INVALID_GRAPH', '직접 순환은 허용하지 않습니다. REPEAT를 사용하세요.');
    if (visited.has(id)) return;
    active.add(id);
    for (const e of edges.filter(e => e.source === id)) visit(e.target);
    active.delete(id); visited.add(id);
  }
  visit(nodes.find(n => n.kind === 'START')!.id);
  if (visited.size !== nodes.length) throw new AppError('INVALID_GRAPH', '시작점에서 도달할 수 없는 노드가 있습니다.');
  for (const n of nodes) {
    if (typeof n.id !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(n.id) || !finite(object(n.position).x) || !finite(object(n.position).y)) invalid('노드 ID·위치 오류');
    const ports = edges.filter(e => e.source === n.id).map(e => e.port as string).sort();
    const owners = nodes.filter(o => ['REPEAT', 'PARALLEL'].includes(String(o.kind)) && o.joinId === n.id);
    let expected: string[] | null = ['next'];
    switch (n.kind) {
      case 'END': expected = []; break;
      case 'IF': condition(n.condition); expected = ['false', 'true']; break;
      case 'REPEAT': expected = ['body', 'next']; break;
      case 'PARALLEL': expected = null; if (ports.length < 2 || ports.length > 4 || new Set(ports).size !== ports.length || ports.some(p => !/^branch:[0-3]$/.test(p))) invalid('병렬 포트 오류'); break;
      case 'JOIN': if (owners.length !== 1) invalid('합류 소유 블록 오류'); expected = owners[0]!.kind === 'REPEAT' ? [] : ['next']; break;
      case 'ACTION': action(n.action, preview); break;
    }
    if (expected && JSON.stringify(ports) !== JSON.stringify(expected)) invalid('노드 연결 포트 오류');
    if (['REPEAT', 'PARALLEL'].includes(String(n.kind)) && !nodes.some(x => x.id === n.joinId && x.kind === 'JOIN')) invalid('합류 노드 오류');
    if (n.kind === 'REPEAT' && (!Number.isInteger(n.repeatCount) || Number(n.repeatCount) < 1 || Number(n.repeatCount) > 20)) throw new AppError('INVALID_GRAPH', '반복은 1~20회입니다.');
    if (['WAIT', 'TIMER'].includes(String(n.kind)) && (!Number.isInteger(n.durationSeconds) || Number(n.durationSeconds) < 1 || Number(n.durationSeconds) > 7200)) throw new AppError('INVALID_GRAPH', '대기 시간은 1~7200초입니다.');
  }
  const map = new Map(nodes.map(n => [n.id, n]));
  function members(id: unknown, stop: unknown, seen: Set<unknown>): boolean {
    if (id === stop) return true;
    if (seen.has(id)) return true;
    seen.add(id); const n = map.get(id)!; if (n.kind === 'END') return false;
    const next = edges.filter(e => e.source === id);
    if (n.kind === 'REPEAT') {
      const body = next.find(e => e.port === 'body')!, tail = next.find(e => e.port === 'next')!;
      if (!members(body.target, n.joinId, seen)) return false;
      seen.add(n.joinId); return members(tail.target, stop, seen);
    }
    return next.length > 0 && next.every(e => members(e.target, stop, seen));
  }
  function resource(n: ObjectValue): string | null {
    if (n.kind !== 'ACTION') return null;
    const a = object(n.action), type = String(a.type);
    if (['CALENDAR_READ', 'PLACE_SEARCH', 'WHAT3WORDS_RESOLVE'].includes(type)) return null;
    if (['NAVIGATE', 'CANCEL_NAVIGATION'].includes(type)) return 'navigation';
    if (['PLAY_LOCAL_AUDIO', 'STOP_LOCAL_AUDIO', 'MEDIA_CONTROL'].includes(type)) return 'media';
    return type === 'HA_CALL' ? 'HA:' + a.targetId : type;
  }
  for (const owner of nodes.filter(n => ['REPEAT', 'PARALLEL'].includes(String(n.kind)))) {
    const entries = edges.filter(e => e.source === owner.id && e.port !== 'next');
    const branches = entries.map(entry => { const set = new Set<unknown>(); if (!members(entry.target, owner.joinId, set)) invalid('분기 합류 오류'); return set; });
    branches.forEach((set, index) => { if (edges.some(e => set.has(e.target) && !set.has(e.source) && e !== entries[index])) invalid('외부 분기 진입'); });
    const all = new Set(branches.flatMap(b => [...b]));
    if (edges.some(e => e.target === owner.joinId && !all.has(e.source) && !entries.includes(e))) invalid('외부 합류 진입');
    if (owner.kind === 'PARALLEL') for (let i = 0; i < branches.length; i++) for (let j = 0; j < i; j++) {
      if ([...branches[i]!].some(id => branches[j]!.has(id))) invalid('병렬 분기 교차');
      const resources = new Set([...branches[i]!].map(id => resource(map.get(id)!)).filter(Boolean));
      if ([...branches[j]!].some(id => { const r = resource(map.get(id)!); return r !== null && resources.has(r); })) invalid('병렬 자원 충돌');
    }
  }
  // Same node-visit semantics as the app: repeats multiply, parallel sums, IF takes max.
  // Structure is valid here; saturate at limit+1 to avoid arithmetic overflow.
  const visitMemo = new Map<string, number>();
  function visits(id: unknown, stop: unknown): number {
    if (id === stop) return 0;
    const key = JSON.stringify([id, stop]);
    const cached = visitMemo.get(key); if (cached !== undefined) return cached;
    const node = map.get(id)!;
    const next = edges.filter(e => e.source === id);
    const target = (port: string) => next.find(e => e.port === port)!.target;
    let following: number;
    switch (node.kind) {
      case 'END': following = 0; break;
      case 'IF': following = Math.max(...next.map(e => visits(e.target, stop))); break;
      case 'REPEAT': following = visits(target('body'), node.joinId) * Number(node.repeatCount) + visits(target('next'), stop); break;
      case 'PARALLEL': following = next.reduce((sum,e) => sum + visits(e.target, node.joinId), 0) + visits(node.joinId, stop); break;
      default: following = visits(target('next'), stop);
    }
    const total = Math.min(4097, 1 + following); visitMemo.set(key,total); return total;
  }
  if (visits(nodes.find(n => n.kind === 'START')!.id, null) > 4096) invalid('최대 실행 경로가 4,096회 노드 방문을 초과할 수 있습니다.');
  // App remains authoritative for live permissions, capability, approval and physical execution.
  return w;
}
export function validateResponse(value: unknown, mode: string, base?: ObjectValue, selectedNodeId?: string, vehicleAreas?: VehicleAreas[]): ObjectValue {
  if (!responseCheck(value)) throw new AppError('INVALID_AI_OUTPUT', 'AI 응답 형식을 검증하지 못했습니다.', 502);
  const result = object(value);
  if (result.status !== 'draft') {
    if (result.patch !== null) throw new AppError('INVALID_AI_OUTPUT', '질문·미지원 응답에 수정 패치가 있습니다.', 502);
    if (result.status === 'needs_clarification') {
      const questions = result.questions as string[];
      if (!questions.length || !questions.every(text)) throw new AppError('INVALID_AI_OUTPUT', '보충 질문을 확인하세요.', 502);
    }
    if (result.workflow !== null) {
      if (mode !== 'create' || result.status !== 'needs_clarification') throw new AppError('INVALID_AI_OUTPUT', '그래프 미리 보기는 생성 시 보충 질문에만 사용할 수 있습니다.', 502);
      const preview = checkWorkflow(result.workflow, true);
      if (vehicleAreas) checkAreaHints(preview, vehicleAreas);
    }
    return result;
  }
  if (mode === 'create') {
    if (result.patch !== null) throw new AppError('INVALID_AI_OUTPUT', '생성 응답에 수정 패치를 사용할 수 없습니다.', 502);
    const draft = validateWorkflow(result.workflow);
    if (vehicleAreas) checkAreaHints(draft, vehicleAreas);
  } else {
    if (!base || result.workflow !== null) throw new AppError('INVALID_AI_OUTPUT', '편집은 기준 버전에 대한 패치여야 합니다.', 502);
    const patch = object(result.patch);
    if (patch.baseRevision !== base.revision) throw new AppError('STALE_PATCH', 'AI 패치 기준 버전이 다릅니다.', 409);
    const operations = patch.operations as ObjectValue[];
    if (!operations.length) throw new AppError('INVALID_AI_OUTPUT', '빈 패치입니다.', 502);
    if (selectedNodeId !== undefined) validateSelectedOperations(base, selectedNodeId, operations);
    const next = structuredClone(base);
    let nodes = next.nodes as ObjectValue[], edges = next.edges as ObjectValue[];
    for (const op of operations) {
      switch (op.type) {
        case 'REPLACE_NODE': { const n = object(op.node); if (n.id !== op.nodeId || !nodes.some(x => x.id === n.id)) throw new AppError('INVALID_PATCH', '교체 노드가 없습니다.'); nodes = nodes.map(x => x.id === n.id ? n : x); break; }
        case 'ADD_NODE': nodes.push(object(op.node)); break;
        case 'REMOVE_NODE': if (!nodes.some(n => n.id === op.nodeId)) throw new AppError('INVALID_PATCH', '삭제 노드가 없습니다.'); nodes = nodes.filter(n => n.id !== op.nodeId); edges = edges.filter(e => e.source !== op.nodeId && e.target !== op.nodeId); break;
        case 'CONNECT': edges.push(object(op.edge)); break;
        case 'DISCONNECT': if (!edges.some(e => e.id === op.edgeId)) throw new AppError('INVALID_PATCH', '삭제 연결이 없습니다.'); edges = edges.filter(e => e.id !== op.edgeId); break;
        case 'CHANGE_TITLE': next.title = op.title; break;
        case 'CHANGE_TRIGGER': next.trigger = op.trigger; break;
      }
    }
    next.nodes = nodes; next.edges = edges; validateWorkflow(next);
    if (vehicleAreas) checkAreaHints(next, vehicleAreas, base);
  }
  return result;
}
