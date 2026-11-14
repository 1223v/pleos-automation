import type { ObjectValue } from './contracts.js';
import { AppError } from './errors.js';

const cabinActions = new Set(['SET_HVAC_TARGET', 'SET_AMBIENT_BRIGHTNESS', 'SET_AMBIENT_COLOR', 'SET_SEAT_HEAT', 'MOVE_SEAT_LIMITED']);

export interface VehicleAreas {
  type: string;
  areas: string[];
  axis?: 'FORE_AFT' | 'BACKREST';
}

function invalidHints(): never {
  throw new AppError('INVALID_INPUT', '차량 동작별 영역 목록을 확인하세요.');
}

function rawArea(value: unknown): value is string {
  return typeof value === 'string' && value.length <= 160 &&
    /^[A-Z][A-Z0-9_]*(?:\+[A-Z][A-Z0-9_]*)*$/.test(value) && !value.split('+').includes('DRIVER');
}

export function readVehicleAreas(value: unknown): VehicleAreas[] {
  if (!Array.isArray(value) || value.length > 6) invalidHints();
  const seen = new Set<string>();
  return value.map((entry: unknown) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) invalidHints();
    const { type, axis, areas } = entry as ObjectValue;
    if (Object.keys(entry).some(key => !['type', 'axis', 'areas'].includes(key)) ||
        typeof type !== 'string' || !cabinActions.has(type) || !Array.isArray(areas) || areas.length > 32 ||
        !areas.every(rawArea) || new Set(areas).size !== areas.length) invalidHints();
    if (type === 'MOVE_SEAT_LIMITED' ? axis !== 'FORE_AFT' && axis !== 'BACKREST' : 'axis' in entry) invalidHints();
    const key = type + '/' + (axis ?? '');
    if (seen.has(key)) invalidHints();
    seen.add(key);
    return axis === 'FORE_AFT' || axis === 'BACKREST' ? { type, axis, areas } : { type, areas };
  });
}

/** Compare newly proposed targets with reported SDK area strings. Runtime access still belongs to the app. */
export function checkAreaHints(workflow: ObjectValue, hints: VehicleAreas[], base?: ObjectValue): void {
  const previous = new Map((base?.nodes as ObjectValue[] | undefined)?.map(node => [node.id, node]));
  for (const node of workflow.nodes as ObjectValue[]) {
    if (node.kind !== 'ACTION') continue;
    const action = node.action as ObjectValue;
    if (action.area === '') continue; // Only an unresolved seat on a question preview passes graph validation.
    const old = previous.get(node.id);
    const earlierAction = old?.kind === 'ACTION' ? old.action as ObjectValue : undefined;
    if (earlierAction && earlierAction.type === action.type && earlierAction.area === action.area && earlierAction.axis === action.axis) continue;
    const hint = hints.find(item => item.type === action.type && (!item.axis || item.axis === action.axis));
    if (hint && !hint.areas.includes(String(action.area)))
      throw new AppError('INVALID_AI_OUTPUT', '제안된 동작 영역이 앱에서 전달한 영역 목록과 다릅니다.', 502);
  }
}
