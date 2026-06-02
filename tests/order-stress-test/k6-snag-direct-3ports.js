/**
 * 直连三台 order：8081 / 8083 / 8085，三台各自恒定 RPS。
 *
 * 默认三段阶梯（每台）：2000 → 4000 → 6000 iter/s，合计约 6000 → 12000 → 18000 req/s。
 * 每段时长默认 5 分钟（STEP_SEC=300），整段总时长约 15 分钟 + graceful stop。（整次约千万级请求，注意磁盘与 summary 体积。）
 *
 * 环境变量：
 *   HOST           默认 localhost
 *   RPS_STEPS      逗号分隔，默认 2000,4000,6000
 *   STEP_SEC       每段秒数，默认 300（5m）
 *   SUMMARY_OUT    汇总 JSON 路径，默认 summary.json
 *   VU_PRE_CAP     preAllocated 上限，默认 12000（极高压下可调大）
 *   VU_MAX_CAP     maxVUs 硬顶，默认 80000（防单场景撑爆单机；不足会 dropped_iterations）
 *
 * 用法（建议在仓库根目录执行）：
 *   k6 run tests/order-stress-test/k6-snag-direct-3ports.js
 *   k6 run -e HOST=192.168.31.253 tests/order-stress-test/k6-snag-direct-3ports.js
 *   k6 run -e STEP_SEC=120 -e RPS_STEPS=2000,4000,6000 tests/order-stress-test/k6-snag-direct-3ports.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const HOST = __ENV.HOST || 'localhost';
const STEP_SEC = Number(__ENV.STEP_SEC || 300);
const RPS_STEPS = __ENV.RPS_STEPS
  ? __ENV.RPS_STEPS.split(',').map((s) => Number(s.trim()))
  : [2000, 4000, 6000];
const SUMMARY_OUT = __ENV.SUMMARY_OUT || 'summary.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 12000);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 80000);

const BASES = [
  `http://${HOST}:8081`,
  `http://${HOST}:8083`,
  `http://${HOST}:8085`,
];

const PORTS = [
  { id: '8081', exec: 'hit8081' },
  { id: '8083', exec: 'hit8083' },
  { id: '8085', exec: 'hit8085' },
];

function vuBudget(rps) {
  const pre = Math.max(500, Math.min(VU_PRE_CAP, Math.ceil(rps * 0.55)));
  const rawMax = Math.max(pre + 1000, Math.ceil(rps * 10));
  const max = Math.min(VU_MAX_CAP, rawMax);
  return { preAllocatedVUs: pre, maxVUs: max };
}

const maxRps = Math.max(...RPS_STEPS);
const b = vuBudget(maxRps);
const stepDuration = `${STEP_SEC}s`;

function buildScenarios() {
  const scenarios = {};
  for (let i = 0; i < RPS_STEPS.length; i++) {
    const rps = RPS_STEPS[i];
    for (const p of PORTS) {
      scenarios[`step${i + 1}_port_${p.id}`] = {
        executor: 'constant-arrival-rate',
        rate: rps,
        timeUnit: '1s',
        duration: stepDuration,
        startTime: `${i * STEP_SEC}s`,
        preAllocatedVUs: b.preAllocatedVUs,
        maxVUs: b.maxVUs,
        exec: p.exec,
        tags: { port: p.id, step: String(i + 1), rps_per_port: String(rps) },
      };
    }
  }
  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: buildHttpThresholds(),
  summaryTrendStats: SUMMARY_TREND_STATS,
};

const PATH = '/api/order/snag';

function postSnag(baseUrl, portTag) {
  const body = JSON.stringify({
    userId: `k6_${portTag}_${__VU}_${__ITER}_${Date.now()}`,
  });
  const res = http.post(
    `${baseUrl}${PATH}`,
    body,
    postJsonParams({
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: { port: portTag },
    }),
  );
  check(res, {
    [`${portTag} status 200`]: (r) => r.status === 200,
  });
}

export function hit8081() {
  postSnag(BASES[0], '8081');
}
export function hit8083() {
  postSnag(BASES[1], '8083');
}
export function hit8085() {
  postSnag(BASES[2], '8085');
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [SUMMARY_OUT]: JSON.stringify(data, null, 2),
  };
}
