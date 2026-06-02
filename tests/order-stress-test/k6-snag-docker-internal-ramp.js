/**
 * 与 order 同 Docker 网络：按多台 RPS 阶梯加压，便于观察拐点（http_req_failed、dropped_iterations；
 * 容器 CPU 请在宿主用 `docker stats` 过滤 compose 副本，例如：
 *   docker stats $(docker ps -q -f label=com.docker.compose.service=singularity-order)
 *
 * 每台端口各自恒定 RPS，三档合计约 3 × 各档 RPS。
 *
 * 环境变量：
 *   ORDER_TARGETS  可选，3 个逗号分隔基址（无 path），三车道各打一容器；见 k6-snag-docker-internal.js 与 tests/order-stress-test/k6-print-order-targets.*
 *   RPS_RAMP      逗号分隔，每档每台 it/s，默认 800,1200,1600,2000,2400（可改细/改粗）
 *   STEP_SEC      每一档持续秒数，默认 120（2 分钟）
 *   SUMMARY_OUT   默认 /out/summary-ramp.json（compose 挂载 k6-out 时落在宿主同目录）
 *   VU_PRE_CAP / VU_MAX_CAP  与 flat 脚本相同含义
 *
 * compose（见 k6-order-ramp 服务）：
 *   docker compose -f docker-compose.backend.yml --profile k6 run --rm k6-order-ramp
 *
 * 本地 k6（仅调试脚本语法）：
 *   k6 run tests/order-stress-test/k6-snag-docker-internal-ramp.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const STEP_SEC = Number(__ENV.STEP_SEC || 120);
const RPS_RAMP = __ENV.RPS_RAMP
  ? __ENV.RPS_RAMP.split(',').map((s) => Number(s.trim()))
  : [800, 1200, 1600, 2000, 2400];
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '/out/summary-ramp.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 12000);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 80000);

const ORDER_BASE = __ENV.ORDER_BASE || 'http://singularity-order:8081';

function buildLaneBases() {
  const raw = __ENV.ORDER_TARGETS;
  if (raw && String(raw).trim()) {
    const parts = String(raw)
      .split(',')
      .map((s) => s.trim().replace(/\/$/, ''))
      .filter(Boolean);
    if (parts.length !== 3) {
      throw new Error(
        `ORDER_TARGETS 必须恰好 3 个基址（无 path），当前 ${parts.length} 个。`,
      );
    }
    return parts;
  }
  const b = ORDER_BASE.replace(/\/$/, '');
  return [b, b, b];
}

const BASES = buildLaneBases();

const PORTS = [
  { id: 'lane-1', exec: 'hitLane1' },
  { id: 'lane-2', exec: 'hitLane2' },
  { id: 'lane-3', exec: 'hitLane3' },
];

function vuBudget(rps) {
  const pre = Math.max(500, Math.min(VU_PRE_CAP, Math.ceil(rps * 0.55)));
  const rawMax = Math.max(pre + 1000, Math.ceil(rps * 10));
  const max = Math.min(VU_MAX_CAP, rawMax);
  return { preAllocatedVUs: pre, maxVUs: max };
}

const maxRps = Math.max(...RPS_RAMP);
const b = vuBudget(maxRps);
const stepDuration = `${STEP_SEC}s`;

function buildScenarios() {
  const scenarios = {};
  for (let i = 0; i < RPS_RAMP.length; i++) {
    const rps = RPS_RAMP[i];
    for (const p of PORTS) {
      scenarios[`ramp${i + 1}_port_${p.id}`] = {
        executor: 'constant-arrival-rate',
        rate: rps,
        timeUnit: '1s',
        duration: stepDuration,
        startTime: `${i * STEP_SEC}s`,
        preAllocatedVUs: b.preAllocatedVUs,
        maxVUs: b.maxVUs,
        exec: p.exec,
        tags: {
          lane: p.id,
          target: 'docker-internal-ramp',
          ramp_step: String(i + 1),
          rps_per_port: String(rps),
        },
      };
    }
  }
  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: buildHttpThresholds({ failRate: 0.15 }),
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
      tags: { lane: portTag },
    }),
  );
  check(res, {
    [`${portTag} status 200`]: (r) => r.status === 200,
  });
}

export function hitLane1() {
  postSnag(BASES[0], 'lane-1');
}
export function hitLane2() {
  postSnag(BASES[1], 'lane-2');
}
export function hitLane3() {
  postSnag(BASES[2], 'lane-3');
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: false }),
    [SUMMARY_OUT]: JSON.stringify(data, null, 2),
  };
}
