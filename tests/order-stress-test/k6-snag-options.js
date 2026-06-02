/**
 * 各 k6-snag-* 脚本共用的汇总统计与延迟阈值（含 p(99)）。
 * 可调环境变量：HTTP_REQ_P95_MS、HTTP_REQ_P99_MS、HTTP_REQ_FAIL_RATE
 */
export const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'];

/**
 * @param {{ failRate?: number, p95Ms?: number, p99Ms?: number }} [overrides]
 */
export function buildHttpThresholds(overrides = {}) {
  const p95Ms = Number(__ENV.HTTP_REQ_P95_MS ?? overrides.p95Ms ?? 8000);
  const p99Ms = Number(__ENV.HTTP_REQ_P99_MS ?? overrides.p99Ms ?? 12000);
  const failRate = Number(__ENV.HTTP_REQ_FAIL_RATE ?? overrides.failRate ?? 0.08);
  return {
    http_req_failed: [`rate<${failRate}`],
    http_req_duration: [`p(95)<${p95Ms}`, `p(99)<${p99Ms}`],
  };
}
