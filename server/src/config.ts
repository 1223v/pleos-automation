import { resolve } from 'node:path';
import { AppError } from './errors.js';

export interface Config {
  host: string;
  port: number;
  authToken: string;
  aiEnabled: boolean;
  apiKey: string;
  model: 'gpt-6.1-sol';
  dailyBudgetMicros: number;
  monthlyBudgetMicros: number;
  maxOutputTokens: number;
  budgetDbPath: string;
  tlsCertPath: string;
  tlsKeyPath: string;
  connectorsEnabled: boolean;
  googleClientId: string;
  googleClientSecret: string;
  googleRefreshToken: string;
  googleCalendarId: string;
  haBaseUrl: string;
  haAccessToken: string;
  haAllowedEntities: string[];
  kakaoKey: string;
  what3wordsKey: string;
  what3wordsEnabled: boolean;
}

export function isLoopback(host: string): boolean {
  return host === '127.0.0.1' || host === '::1' || host === 'localhost' || host === '::ffff:127.0.0.1';
}

function amount(value: string | undefined, name: string): number {
  const text = value ?? '0';
  if (!/^\d+(\.\d{1,6})?$/.test(text)) throw new AppError('INVALID_CONFIG', `${name}은 0 이상의 USD 금액이어야 합니다.`, 500);
  const micros = Math.round(Number(text) * 1_000_000);
  if (!Number.isSafeInteger(micros)) throw new AppError('INVALID_CONFIG', `${name}이 범위를 초과했습니다.`, 500);
  return micros;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env, cwd = process.cwd()): Config {
  const aiEnabled = env.AI_ENABLED === 'true';
  const connectorsEnabled = env.CONNECTORS_ENABLED === 'true';
  const host = env.HOST ?? '127.0.0.1';
  const port = Number(env.PORT ?? 8787);
  const maxOutputTokens = Number(env.AI_MAX_OUTPUT_TOKENS ?? 8192);
  if (!Number.isInteger(port) || port < 0 || port > 65535 || !Number.isInteger(maxOutputTokens) || maxOutputTokens < 256 || maxOutputTokens > 16384) {
    throw new AppError('INVALID_CONFIG', '포트 또는 출력 토큰 한도가 잘못되었습니다.', 500);
  }
  if (env.OPENAI_MODEL && env.OPENAI_MODEL !== 'gpt-6.1-sol') throw new AppError('INVALID_CONFIG', '모델 변경 전 가격과 평가 기준을 갱신해야 합니다.', 500);
  const tlsCertPath = env.TLS_CERT_PATH ?? '';
  const tlsKeyPath = env.TLS_KEY_PATH ?? '';
  if (Boolean(tlsCertPath) !== Boolean(tlsKeyPath) || (!isLoopback(host) && (!tlsCertPath || !tlsKeyPath))) {
    throw new AppError('INVALID_CONFIG', '외부 인터페이스는 HTTPS 인증서와 키가 필요합니다.', 500);
  }
  return {
    host, port, authToken: env.SERVER_AUTH_TOKEN ?? '', aiEnabled,
    apiKey: aiEnabled ? (env.OPENAI_API_KEY ?? '') : '',
    model: 'gpt-6.1-sol',
    dailyBudgetMicros: amount(env.AI_DAILY_BUDGET_USD, '일 예산'),
    monthlyBudgetMicros: amount(env.AI_MONTHLY_BUDGET_USD, '월 예산'),
    maxOutputTokens, budgetDbPath: resolve(cwd, env.BUDGET_DB_PATH ?? '.data/budget.sqlite'),
    tlsCertPath, tlsKeyPath, connectorsEnabled,
    googleClientId: connectorsEnabled ? (env.GOOGLE_CLIENT_ID ?? '') : '',
    googleClientSecret: connectorsEnabled ? (env.GOOGLE_CLIENT_SECRET ?? '') : '',
    googleRefreshToken: connectorsEnabled ? (env.GOOGLE_REFRESH_TOKEN ?? '') : '',
    googleCalendarId: env.GOOGLE_CALENDAR_ID ?? 'primary',
    haBaseUrl: env.HA_BASE_URL ?? '',
    haAccessToken: connectorsEnabled ? (env.HA_ACCESS_TOKEN ?? '') : '',
    haAllowedEntities: (env.HA_ALLOWED_ENTITIES ?? '').split(',').map(s => s.trim()).filter(Boolean),
    kakaoKey: connectorsEnabled ? (env.KAKAO_REST_API_KEY ?? '') : '',
    what3wordsKey: connectorsEnabled ? (env.WHAT3WORDS_API_KEY ?? '') : '',
    what3wordsEnabled: connectorsEnabled && env.WHAT3WORDS_ENABLED === 'true',
  };
}

export function requireAi(config: Config): void {
  if (!config.aiEnabled || !config.apiKey || config.dailyBudgetMicros <= 0 || config.monthlyBudgetMicros <= 0) {
    throw new AppError('AI_DISABLED', '키와 일·월 예산 설정 및 호출 활성화 전까지 실제 AI 호출을 보류합니다.', 503);
  }
}
