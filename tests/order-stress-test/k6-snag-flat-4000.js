/**
 * 同一 order-lb 在宿主上映射的多个端口各打一车道（默认 8081/8181/8281），总 RPS = 各车道之和。
 * compose 需与 deploy/docker-compose.backend.yml 中 lb 的 ports 一致。
 *
 * 仓库根执行示例：
 *   k6 run tests/order-stress-test/k6-snag-flat-4000.js
 *   k6 run -e HOST=192.168.31.253 tests/order-stress-test/k6-snag-flat-4000.js
 *   k6 run -e RPS=6000 -e DURATION=2m tests/order-stress-test/k6-snag-flat-4000.js
 *   k6 run -e RPS_PER_PORT=2500 tests/order-stress-test/k6-snag-flat-4000.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const HOST = __ENV.HOST || 'localhost';
const DURATION = __ENV.DURATION || '5m';
const SUMMARY_OUT = __ENV.SUMMARY_OUT || 'summary.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 12000);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 80000);

const PORT_LIST = (__ENV.PORTS || '8081,8181,8281')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

if (PORT_LIST.length !== 3) {
  throw new Error(
    `期望正好 3 个 lb 宿主端口（与 compose 一致），当前 PORTS=${PORT_LIST.join(',') || '(空)'}`,
  );
}

/** 每端口每秒到达率；未设时按 RPS 总数均分到 3 条车道 */
const RPS_TOTAL = Number(__ENV.RPS != null && __ENV.RPS !== '' ? __ENV.RPS : 9000);
const RPS_PER_PORT =
  __ENV.RPS_PER_PORT != null && __ENV.RPS_PER_PORT !== ''
    ? Number(__ENV.RPS_PER_PORT)
    : Math.max(1, Math.floor(RPS_TOTAL / PORT_LIST.length));

function vuBudget(rps) {
  const pre = Math.max(500, Math.min(VU_PRE_CAP, Math.ceil(rps * 0.55)));
  const rawMax = Math.max(pre + 1000, Math.ceil(rps * 10));
  const max = Math.min(VU_MAX_CAP, rawMax);
  return { preAllocatedVUs: pre, maxVUs: max };
}

const b = vuBudget(RPS_PER_PORT);

const PATH = '/api/order/snag';

function postSnag(port) {
  const base = `http://${HOST}:${port}`;
  const body = JSON.stringify({
    userId: `k6_${port}_${__VU}_${__ITER}_${Date.now()}`,
  });
  const res = http.post(
    `${base}${PATH}`,
    body,
    postJsonParams({
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: { port },
    }),
  );
  check(res, {
    [`${port} status 200`]: (r) => r.status === 200,
  });
}

export function hitLane0() {
  postSnag(PORT_LIST[0]);
}
export function hitLane1() {
  postSnag(PORT_LIST[1]);
}
export function hitLane2() {
  postSnag(PORT_LIST[2]);
}

const execNames = ['hitLane0', 'hitLane1', 'hitLane2'];

export const options = {
  scenarios: {
    [`lb_${PORT_LIST[0]}`]: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: execNames[0],
      tags: { port: PORT_LIST[0] },
    },
    [`lb_${PORT_LIST[1]}`]: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: execNames[1],
      tags: { port: PORT_LIST[1] },
    },
    [`lb_${PORT_LIST[2]}`]: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: execNames[2],
      tags: { port: PORT_LIST[2] },
    },
  },
  thresholds: buildHttpThresholds(),
  summaryTrendStats: SUMMARY_TREND_STATS,
};

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [SUMMARY_OUT]: JSON.stringify(data, null, 2),
  };
}
