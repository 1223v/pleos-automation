// Local AVD fixture only. It never reads .env, creates a provider client, or calls an external API.
// Build server first, then run this file with the project Node 24 executable.
import { createApp } from '../../server/dist/src/http.js';
import { loadConfig } from '../../server/dist/src/config.js';
import { BudgetLedger } from '../../server/dist/src/budget.js';

globalThis.fetch = async () => { throw new Error('External network disabled in A05 fixture'); };
const config = loadConfig({
  HOST: '127.0.0.1', PORT: '18787',
  SERVER_AUTH_TOKEN: 'a05-local-fixture-token-000000000000',
  AI_ENABLED: 'true', OPENAI_API_KEY: 'local-fixture-not-a-provider-key',
  AI_DAILY_BUDGET_USD: '10', AI_MONTHLY_BUDGET_USD: '100',
  CONNECTORS_ENABLED: 'false',
});
const ledger = new BudgetLedger(':memory:');
let calls = 0;
const provider = async (input, _config, specification) => {
  if (specification?.name !== 'run_explanation_v1') throw new Error('Only explanation fixture requests are supported');
  const request = JSON.parse(input);
  const first = request.run.events[0];
  calls++;
  process.stdout.write(`MOCK_EXPLANATION calls=${calls} events=${request.run.events.length}\n`);
  return {
    value: {
      runId: request.run.runId,
      summary: '모의 설명: 실행 이력 근거 확인',
      details: first ? [{ nodeId: first.nodeId, explanation: '앱이 전송한 첫 번째 기록을 참조한 로컬 모의 응답입니다.', eventSequences: [first.sequence] }] : [],
      limitations: ['실제 AI 호출 없이 로컬 HTTP 연결과 근거 참조만 시험합니다.'],
    },
    inputTokens: 100, outputTokens: 50,
  };
};
const server = createApp(config, ledger, provider);
server.listen(config.port, config.host, () => process.stdout.write('READY http://127.0.0.1:18787 MOCK_ONLY external_fetch_disabled\n'));
let stopping = false;
function stop() {
  if (stopping) return;
  stopping = true;
  server.close(() => { ledger.close(); process.exit(0); });
  server.closeIdleConnections();
}
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
server.on('error', error => { process.stderr.write(`LOCAL_FIXTURE_ERROR ${error.code ?? 'UNKNOWN'}\n`); ledger.close(); process.exitCode = 1; });
