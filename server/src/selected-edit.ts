import { isDeepStrictEqual } from 'node:util';
import type { ObjectValue } from './contracts.js';
import { AppError } from './errors.js';

function outsideSelection(): never {
  throw new AppError('INVALID_PATCH', '선택한 카드와 그 다음 단계의 편집 범위를 벗어났습니다.', 502);
}

function part(value: unknown): ObjectValue {
  if (!value || typeof value !== 'object' || Array.isArray(value)) outsideSelection();
  return value as ObjectValue;
}

function editableActionField(type: unknown): 'value' | 'text' | undefined {
  switch (type) {
    case 'SET_HVAC_TARGET':
    case 'SET_AMBIENT_BRIGHTNESS':
    case 'SET_SEAT_HEAT': return 'value';
    case 'SET_AMBIENT_COLOR': return 'text';
  }
}

function matchesAction(original: ObjectValue, proposed: ObjectValue): boolean {
  const field = editableActionField(original.type);
  const expected = field ? { ...original, [field]: proposed[field] } : original;
  return isDeepStrictEqual(expected, proposed);
}

function checkReplacement(selected: ObjectValue, replacement: ObjectValue): void {
  const expected: ObjectValue = { ...selected, label: replacement.label };
  switch (selected.kind) {
    case 'IF': expected.condition = replacement.condition; break;
    case 'WAIT':
    case 'TIMER': expected.durationSeconds = replacement.durationSeconds; break;
    case 'REPEAT': expected.repeatCount = replacement.repeatCount; break;
    case 'ACTION':
      if (!matchesAction(part(selected.action), part(replacement.action))) outsideSelection();
      expected.action = replacement.action;
      break;
  }
  if (!isDeepStrictEqual(replacement, expected)) outsideSelection();
}

function checkAddition(selected: ObjectValue, added: ObjectValue): void {
  if (added.condition !== null || added.joinId !== null || added.repeatCount !== null) outsideSelection();
  if (added.kind === 'WAIT' && added.action === null) return;
  if (selected.kind !== 'ACTION' || added.kind !== 'ACTION' || added.durationSeconds !== null) outsideSelection();
  const original = part(selected.action);
  if (!editableActionField(original.type) || !matchesAction(original, part(added.action))) outsideSelection();
}

/** Check every operation, including intermediate edits that would disappear from a final graph diff. */
export function validateSelectedOperations(base: ObjectValue, selectedId: string, operations: ObjectValue[]): void {
  const nodes = base.nodes as ObjectValue[], edges = base.edges as ObjectValue[];
  const selected = nodes.find(node => node.id === selectedId);
  if (!selected) outsideSelection();
  const outgoing = edges.filter(edge => edge.source === selectedId);
  const successor = outgoing.length === 1 && outgoing[0]!.port === 'next' ? outgoing[0] : undefined;
  const added = new Set<unknown>();
  const next = new Map<unknown, unknown>();
  const edgeIds = new Set(edges.map(edge => edge.id));
  let disconnected = false;
  for (const op of operations) {
    switch (op.type) {
      case 'REPLACE_NODE':
        if (op.nodeId !== selectedId) outsideSelection();
        checkReplacement(selected, part(op.node));
        break;
      case 'ADD_NODE': {
        const node = part(op.node);
        if (added.has(node.id) || nodes.some(existing => existing.id === node.id)) outsideSelection();
        checkAddition(selected, node);
        added.add(node.id);
        break;
      }
      case 'DISCONNECT':
        if (!successor || disconnected || op.edgeId !== successor.id) outsideSelection();
        disconnected = true;
        edgeIds.delete(successor.id);
        break;
      case 'CONNECT': {
        const edge = part(op.edge);
        if (edge.port !== 'next' || next.has(edge.source) || edgeIds.has(edge.id)) outsideSelection();
        next.set(edge.source, edge.target);
        edgeIds.add(edge.id);
        break;
      }
      default: outsideSelection();
    }
  }
  if (!disconnected && !added.size && !next.size) return;
  if (!successor || !disconnected || !added.size || next.size !== added.size + 1) outsideSelection();
  // The complete new path must consume every added node once and rejoin the original successor.
  let target = next.get(selectedId);
  while (added.delete(target)) target = next.get(target);
  if (added.size || target !== successor.target) outsideSelection();
}
