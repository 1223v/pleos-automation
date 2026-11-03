import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { AppError } from './errors.js';

// Standard price $2 input/$10 output per 1M, cache writes $2.50 (2026-10-27).
// Conservatively charge $3 per 1M input in this local budget ledger, not a billing invoice.
export const INPUT_MICROS = 3;
export const OUTPUT_MICROS = 10;

export class BudgetLedger {
  private readonly db: DatabaseSync;
  constructor(path: string, private readonly now: () => Date = () => new Date()) {
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA busy_timeout = 5000; PRAGMA journal_mode = WAL;');
    this.db.exec(`CREATE TABLE IF NOT EXISTS requests (
      request_id TEXT PRIMARY KEY, day TEXT NOT NULL, month TEXT NOT NULL,
      charged_micros INTEGER NOT NULL CHECK(charged_micros >= 0),
      state TEXT NOT NULL CHECK(state IN ('RESERVED','SETTLED','UNCERTAIN'))
    ) STRICT;`);
    // Old rows have only a UTC date. Keep them distinguishable: their exact time is unknown.
    const columns = this.db.prepare('PRAGMA table_info(requests)').all();
    if (!columns.some(c => c.name === 'created_at')) this.db.exec('ALTER TABLE requests ADD COLUMN created_at TEXT');
    this.db.exec('CREATE TABLE IF NOT EXISTS budget_control (id INTEGER PRIMARY KEY CHECK(id=1), blocked INTEGER NOT NULL DEFAULT 0) STRICT; INSERT OR IGNORE INTO budget_control(id,blocked) VALUES(1,0);');
  }

  private periods() {
    const now = this.now();
    const day = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
    return { day, month: day.slice(0, 7), instant: now.toISOString() };
  }

  private isBlocked(): boolean { return this.db.prepare('SELECT blocked FROM budget_control WHERE id=1').get()?.blocked === 1; }

  reserve(requestId: string, upperBoundMicros: number, dailyLimit: number, monthlyLimit: number): void {
    if (!Number.isSafeInteger(upperBoundMicros) || upperBoundMicros <= 0) throw new AppError('INVALID_RESERVATION', '비용 예약값이 잘못되었습니다.', 500);
    if (![dailyLimit, monthlyLimit].every(v => Number.isSafeInteger(v) && v >= 0)) throw new AppError('INVALID_CONFIG', '예산 한도가 잘못되었습니다.', 500);
    const { day, month, instant } = this.periods();
    this.db.exec('BEGIN IMMEDIATE');
    try {
      if (this.isBlocked()) throw new AppError('BUDGET_LOCKED', '사용량 불일치로 AI 호출이 잠겼습니다. 예산 장부를 점검해야 합니다.', 503);
      if (this.db.prepare('SELECT 1 FROM requests WHERE request_id = ?').get(requestId)) {
        throw new AppError('DUPLICATE_REQUEST', '이미 접수된 요청입니다. 자동 재호출하지 않습니다.', 409);
      }
      const daily = this.total('day', day), monthly = this.total('month', month);
      if (daily + upperBoundMicros > dailyLimit || monthly + upperBoundMicros > monthlyLimit) {
        throw new AppError('BUDGET_EXCEEDED', '설정한 AI 예산으로 이 요청을 예약할 수 없습니다.', 429);
      }
      this.db.prepare('INSERT INTO requests(request_id,day,month,charged_micros,state,created_at) VALUES (?, ?, ?, ?, ?, ?)').run(requestId, day, month, upperBoundMicros, 'RESERVED', instant);
      this.db.exec('COMMIT');
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }

  settle(requestId: string, inputTokens: number, outputTokens: number): void {
    let uncertain = false;
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const row = this.db.prepare('SELECT charged_micros,state FROM requests WHERE request_id = ?').get(requestId);
      if (!row || row.state !== 'RESERVED') throw new AppError('INVALID_SETTLEMENT', '정산 가능한 비용 예약이 아닙니다.', 409);
      const cost = inputTokens * INPUT_MICROS + outputTokens * OUTPUT_MICROS;
      const valid = [inputTokens, outputTokens, cost].every(Number.isSafeInteger) && inputTokens >= 0 && outputTokens >= 0;
      uncertain = !valid || cost > Number(row.charged_micros);
      const charged = uncertain ? Math.max(Number(row.charged_micros), valid ? cost : 0) : cost;
      this.db.prepare('UPDATE requests SET charged_micros=?,state=? WHERE request_id=?').run(charged, uncertain ? 'UNCERTAIN' : 'SETTLED', requestId);
      if (uncertain) this.db.exec('UPDATE budget_control SET blocked=1 WHERE id=1');
      this.db.exec('COMMIT');
    } catch (error) {
      this.db.exec('ROLLBACK'); throw error;
    }
    if (uncertain) throw new AppError('USAGE_UNCERTAIN', '비용을 보수적으로 보존하고 추가 AI 호출을 잠갔습니다.', 502);
  }

  markUncertain(requestId: string): void {
    this.db.prepare('UPDATE requests SET state = ? WHERE request_id = ? AND state = ?').run('UNCERTAIN', requestId, 'RESERVED');
  }

  snapshot(): { day: string; month: string; dailyMicros: number; monthlyMicros: number; unresolved: number; blocked: boolean; timeZone: string } {
    const { day, month } = this.periods();
    const row = this.db.prepare("SELECT COUNT(*) AS count FROM requests WHERE state != 'SETTLED'").get();
    return { day, month, dailyMicros: this.total('day', day), monthlyMicros: this.total('month', month), unresolved: Number(row?.count ?? 0), blocked: this.isBlocked(), timeZone: 'Asia/Seoul' };
  }

  private total(column: 'day' | 'month', value: string): number {
    const start = new Date((column === 'day' ? value : value + '-01') + 'T00:00:00+09:00');
    const end = new Date(start);
    if (column === 'day') end.setUTCDate(end.getUTCDate() + 1);
    else { // Compute next local month, avoiding UTC's previous-month date at Korean midnight.
      const [year, month] = value.split('-').map(Number);
      end.setTime(Date.UTC(year!, month!, 1) - 9 * 3_600_000);
    }
    const legacyFrom = start.toISOString().slice(0, 10), legacyTo = new Date(end.getTime() - 1).toISOString().slice(0, 10);
    return Number(this.db.prepare(`SELECT COALESCE(SUM(charged_micros),0) AS total FROM requests WHERE
      (created_at IS NOT NULL AND ${column}=?) OR (created_at IS NULL AND day>=? AND day<=?)`).get(value, legacyFrom, legacyTo)?.total ?? 0);
  }

  close(): void { this.db.close(); }
}
