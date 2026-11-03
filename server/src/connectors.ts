import { Config } from './config.js';
import { object, ObjectValue } from './contracts.js';
import { AppError } from './errors.js';

type Result = { status: string; message: string; observedValue: string | null };
function result(status: string, message: string, value: unknown = null): { result: Result } { return { result: { status, message, observedValue: value === null ? null : JSON.stringify(value) } }; }
function record(value: unknown): value is ObjectValue { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function invalidOutput(): never { throw new AppError('INVALID_CONNECTOR_OUTPUT', '연결 서비스의 조회 결과 형식을 확인하지 못했습니다.', 502); }
function responseObject(value: unknown): ObjectValue { if (!record(value) || 'error' in value) invalidOutput(); return value; }
function nonempty(value: unknown): value is string { return typeof value === 'string' && value.trim().length > 0; }
function coordinate(value: unknown, bound: number): boolean { return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= bound; }
function coordinateText(value: unknown, bound: number): boolean { return nonempty(value) && coordinate(Number(value), bound); }
function calendarEvent(value: unknown): boolean {
  if (!record(value) || !nonempty(value.id) || value.summary !== undefined && typeof value.summary !== 'string') return false;
  // Partial responses and cancelled events can omit these fields; never require a title.
  return ['start', 'end'].every(key => {
    const at = value[key];
    return at === undefined || record(at) && (nonempty(at.date) || nonempty(at.dateTime));
  });
}
export class Connectors {
  constructor(private c: Config, private fetcher: typeof fetch = fetch) {}
  private async json(url: string, init: RequestInit = {}): Promise<unknown> {
    const response = await this.fetcher(url, { ...init, redirect: 'error', signal: AbortSignal.timeout(15_000) });
    if (!response.ok) throw new AppError('CONNECTOR_FAILED', `연결 서비스 요청 실패 (${response.status})`, 502);
    const length = Number(response.headers.get('content-length') ?? 0);
    if (length > 262144) throw new AppError('TOO_LARGE', '연결 서비스 응답이 너무 큽니다.', 502);
    const reader = response.body?.getReader(); if (!reader) throw new AppError('CONNECTOR_FAILED', '응답이 비어 있습니다.', 502);
    let size = 0; const parts: Uint8Array[] = [];
    try { for (;;) { const chunk = await reader.read(); if (chunk.done) break; size += chunk.value.length; if (size > 262144) throw new AppError('TOO_LARGE', '응답 크기 제한 초과', 502); parts.push(chunk.value); } }
    finally { await reader.cancel(); }
    return JSON.parse(Buffer.concat(parts).toString('utf8')) as unknown;
  }
  async execute(body: unknown): Promise<{ result: Result }> {
    if (!this.c.connectorsEnabled) throw new AppError('CONNECTORS_DISABLED', '외부 연결은 계정·허용 대상 설정 전까지 꺼져 있습니다.', 503);
    const a = object(object(body).action);
    switch (a.type) {
      case 'CALENDAR_READ': {
        if (!this.c.googleClientId || !this.c.googleClientSecret || !this.c.googleRefreshToken) throw new AppError('NOT_CONFIGURED', 'Google Calendar OAuth 읽기 권한 설정이 필요합니다.', 503);
        const auth = object(await this.json('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ client_id: this.c.googleClientId, client_secret: this.c.googleClientSecret, refresh_token: this.c.googleRefreshToken, grant_type: 'refresh_token' }) }));
        if (typeof auth.access_token !== 'string') throw new AppError('AUTH_FAILED', 'Google 인증 응답을 확인하지 못했습니다.', 502);
        const now = new Date(), end = new Date(now.getTime() + 86400000);
        const query = new URLSearchParams({ timeMin: now.toISOString(), timeMax: end.toISOString(), singleEvents: 'true', orderBy: 'startTime', maxResults: '20', fields: 'items(id,summary,start,end)' });
        const events = responseObject(await this.json(`https://www.googleapis.com/calendar/v3/calendars/${encodeURIComponent(this.c.googleCalendarId)}/events?${query}`, { headers: { Authorization: `Bearer ${auth.access_token}` } }));
        const items = events.items === undefined ? [] : events.items;
        if (!Array.isArray(items) || !items.every(calendarEvent)) invalidOutput();
        // Event names are user data; never feed connector text back into compiler instructions.
        return result('VERIFIED', '앞으로 24시간의 일정 읽기 완료', items);
      }
      case 'PLACE_SEARCH': {
        if (!this.c.kakaoKey) throw new AppError('NOT_CONFIGURED', '카카오 Local REST API 키가 필요합니다.', 503);
        if (typeof a.text !== 'string' || !a.text.trim() || a.text.length > 200) throw new AppError('INVALID_INPUT', '장소 검색어를 확인하세요.');
        const data = responseObject(await this.json(`https://dapi.kakao.com/v2/local/search/keyword.json?${new URLSearchParams({ query: a.text, size: '5' })}`, { headers: { Authorization: `KakaoAK ${this.c.kakaoKey}` } }));
        if (!Array.isArray(data.documents) || !data.documents.every(p => record(p) && nonempty(p.id) && nonempty(p.place_name) && coordinateText(p.x, 180) && coordinateText(p.y, 90))) invalidOutput();
        return result('VERIFIED', '장소 후보 조회 완료 · 목적지 선택 필요', data.documents);
      }
      case 'WHAT3WORDS_RESOLVE': {
        if (!this.c.what3wordsEnabled || !this.c.what3wordsKey) throw new AppError('NOT_CONFIGURED', 'what3words 계정·요금 확인 및 별도 활성화가 필요합니다.', 503);
        if (typeof a.text !== 'string' || a.text.length > 150) throw new AppError('INVALID_INPUT', '세 단어 주소를 확인하세요.');
        const data = responseObject(await this.json(`https://api.what3words.com/v3/convert-to-coordinates?${new URLSearchParams({ words: a.text, key: this.c.what3wordsKey })}`));
        if (!record(data.coordinates) || !coordinate(data.coordinates.lat, 90) || !coordinate(data.coordinates.lng, 180)) invalidOutput();
        return result('VERIFIED', '세 단어 주소의 좌표 조회 완료', data.coordinates);
      }
      case 'HA_CALL': {
        if (!this.c.haBaseUrl || !this.c.haAccessToken) throw new AppError('NOT_CONFIGURED', 'Home Assistant 연결 설정이 필요합니다.', 503);
        const base = new URL(this.c.haBaseUrl);
        if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash || base.pathname !== '/') throw new AppError('INVALID_CONFIG', 'Home Assistant는 관리자가 설정한 HTTPS 원점이어야 합니다.', 503);
        if (typeof a.targetId !== 'string' || !this.c.haAllowedEntities.includes(a.targetId) || !/^(light|climate)\.[a-z0-9_]+$/.test(a.targetId)) throw new AppError('NOT_ALLOWED', '허용된 조명·공조 대상만 실행할 수 있습니다.', 403);
        if (typeof a.text !== 'string' || !['light.turn_on', 'light.turn_off', 'climate.set_temperature'].includes(a.text) || !a.targetId.startsWith(a.text.split('.')[0] + '.')) throw new AppError('NOT_ALLOWED', '허용되지 않은 스마트홈 동작입니다.', 403);
        const [domain, service] = String(a.text).split('.');
        const data: ObjectValue = { entity_id: a.targetId };
        if (domain === 'climate' && (typeof a.value !== 'number' || !Number.isFinite(a.value))) throw new AppError('INVALID_INPUT', '목표 온도가 필요합니다.');
        const current = object(await this.json(`${base.origin}/api/states/${a.targetId}`, { headers: { Authorization: `Bearer ${this.c.haAccessToken}` } }));
        if (!this.usableState(current, a.targetId)) throw new AppError('NOT_AVAILABLE', '기기의 현재 상태를 확인할 수 없습니다.', 503);
        if (domain === 'climate') {
          const attrs = object(current.attributes);
          if (typeof attrs.min_temp !== 'number' || typeof attrs.max_temp !== 'number' || !Number.isFinite(attrs.min_temp) || !Number.isFinite(attrs.max_temp) || attrs.min_temp > attrs.max_temp || Number(a.value) < attrs.min_temp || Number(a.value) > attrs.max_temp) throw new AppError('NOT_ALLOWED', '기기의 공조 범위를 확인할 수 없거나 범위를 벗어났습니다.');
          data.temperature = a.value;
        }
        try {
          await this.json(`${base.origin}/api/services/${domain}/${service}`, { method: 'POST', headers: { Authorization: `Bearer ${this.c.haAccessToken}`, 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
          const state = object(await this.json(`${base.origin}/api/states/${a.targetId}`, { headers: { Authorization: `Bearer ${this.c.haAccessToken}` } }));
          const matches = this.usableState(state, a.targetId) && (domain === 'light' ? state.state === (service === 'turn_on' ? 'on' : 'off') : object(state.attributes).temperature === a.value);
          return matches ? result('VERIFIED', 'Home Assistant가 보고한 목표 상태 확인', state.state) : result('UNKNOWN', '서비스 응답은 받았으나 목표 상태 반영은 미확인');
        } catch { return result('UNKNOWN', '스마트홈 요청 후 결과 불명 · 자동 재전송 안 함'); }
      }
      default: throw new AppError('UNSUPPORTED_ACTION', '등록된 외부 동작이 아닙니다.');
    }
  }
  private usableState(state: ObjectValue, entity: string): boolean {
    return state.entity_id === entity && typeof state.state === 'string' && state.state.length > 0 && !['unknown', 'unavailable'].includes(state.state);
  }
}
