import http, { IncomingMessage, ServerResponse } from 'node:http';
import https from 'node:https';
import { readFileSync } from 'node:fs';
import { timingSafeEqual } from 'node:crypto';
import { Config } from './config.js';
import { AppError, publicError } from './errors.js';
import { BudgetLedger } from './budget.js';
import { catalogue } from './contracts.js';
import { Planner, Provider } from './planner.js';
import { Connectors } from './connectors.js';

export function createApp(config: Config, ledger: BudgetLedger, provider?: Provider, fetcher?: typeof fetch) {
  if (config.authToken.length < 32) throw new AppError('INVALID_CONFIG', 'SERVER_AUTH_TOKEN을 32자 이상으로 설정하세요.', 500);
  const planner = new Planner(config, ledger, provider), connectors = new Connectors(config, fetcher);
  let windowStart = Date.now(), count = 0;
  const handler = async (req: IncomingMessage, res: ServerResponse) => {
    const send = (status: number, body: unknown) => {
      const encoded = JSON.stringify(body);
      if (Buffer.byteLength(encoded) > 262144) throw new AppError('TOO_LARGE', '서버 응답이 앱 수신 한도를 초과했습니다.', 502);
      res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(encoded);
    };
    try {
      const incoming = Buffer.from(req.headers.authorization ?? ''), expected = Buffer.from('Bearer ' + config.authToken);
      if (incoming.length !== expected.length || !timingSafeEqual(incoming, expected)) throw new AppError('UNAUTHORIZED', '서버 접속 토큰을 확인하세요.', 401);
      if (req.headers.origin) throw new AppError('ORIGIN_NOT_ALLOWED', '브라우저 원점 요청은 허용하지 않습니다.', 403);
      if (Date.now() - windowStart >= 60_000) { windowStart = Date.now(); count = 0; }
      if (++count > 60) throw new AppError('RATE_LIMIT', '잠시 후 다시 시도하세요.', 429);
      if (req.method === 'GET' && req.url === '/v1/status') { const budget = ledger.snapshot(); send(200, { aiEnabled: config.aiEnabled && !!config.apiKey && config.dailyBudgetMicros > 0 && config.monthlyBudgetMicros > 0 && !budget.blocked, model: config.model, connectorsEnabled: config.connectorsEnabled, budget, message: '실제 AI 호출은 키·일/월 예산·활성화 설정이 모두 필요합니다.' }); return; }
      if (req.method === 'GET' && req.url === '/v1/catalogue') { send(200, catalogue); return; }
      if (req.method !== 'POST' || !['/v1/routine-drafts', '/v1/run-explanations', '/v1/connector-actions'].includes(req.url ?? '')) throw new AppError('NOT_FOUND', '등록되지 않은 경로입니다.', 404);
      if (req.headers['content-type']?.split(';', 1)[0]?.trim().toLowerCase() !== 'application/json') throw new AppError('INVALID_INPUT', 'application/json 요청이 필요합니다.', 415);
      let size = 0; const chunks: Buffer[] = [];
      for await (const chunk of req) { const bytes = Buffer.from(chunk); size += bytes.length; if (size > 131072) throw new AppError('TOO_LARGE', '요청이 너무 큽니다.', 413); chunks.push(bytes); }
      let body: unknown; try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw new AppError('INVALID_JSON', 'JSON 형식을 확인하세요.'); }
      const answer = req.url === '/v1/routine-drafts' ? await planner.plan(body) : req.url === '/v1/run-explanations' ? await planner.explain(body) : await connectors.execute(body);
      send(200, answer);
    } catch (error) { const safe = publicError(error); if (!res.headersSent) send(safe.status, { error: { code: safe.code, message: safe.message } }); }
  };
  const server = config.tlsCertPath ? https.createServer({ cert: readFileSync(config.tlsCertPath), key: readFileSync(config.tlsKeyPath) }, handler) : http.createServer(handler);
  server.requestTimeout = 70_000; server.headersTimeout = 10_000;
  return server;
}
