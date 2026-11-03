import { loadConfig } from './config.js';
import { BudgetLedger } from './budget.js';
import { createApp } from './http.js';
import { publicError } from './errors.js';

try {
  const config = loadConfig(), ledger = new BudgetLedger(config.budgetDbPath);
  const server = createApp(config, ledger);
  server.listen(config.port, config.host, () => console.log(`Pleos planner listening on ${config.host}:${config.port}; paid AI enabled: ${config.aiEnabled}`));
  for (const signal of ['SIGINT', 'SIGTERM'] as const) process.on(signal, () => { server.close(() => { ledger.close(); process.exit(0); }); });
} catch (error) { console.error(publicError(error).message); process.exitCode = 1; }
