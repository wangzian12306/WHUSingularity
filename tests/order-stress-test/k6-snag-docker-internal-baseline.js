/**
 * 容器网内压测 singularity-order-baseline（端口默认 8085）。
 * /api/order/snag 需要 userId + productId（与 singularity-order 不同）；productId 默认 1001，与 baseline.benchmark.product-id 一致。
 *
 * 三条车道：未设 ORDER_TARGETS 时三条均打 ORDER_BASE；可设 ORDER_TARGETS 为恰好 3 个基址（与 k6-snag-docker-internal.js 相同）。
 *
 * 预热 Redis：deploy/refill-baseline-stock.ps1（或 .sh）；也可用 POST /api/order/admin/stock/reset
 *
 *   单实例压测请用 run-k6-order-baseline-load.ps1（单车道 single-lane.js）。
 *   三车道：docker compose ... run --rm --no-deps -e ORDER_TARGETS=... k6-order-baseline-load（若 compose 中仍保留该服务）
 */
import http from 'k6/http';
import { check } from 'k6';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const RPS_PER_PORT = Number(__ENV.RPS_PER_PORT || 4500);
const DURATION = __ENV.DURATION || '2m';
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '/out/summary-docker-baseline.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 12000);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 80000);

const ORDER_BASE = __ENV.ORDER_BASE || 'http://singularity-order-baseline:8085';
const BASELINE_PRODUCT_ID = String(__ENV.BASELINE_PRODUCT_ID || '1001');

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

function vuBudget(rps) {
  const pre = Math.max(500, Math.min(VU_PRE_CAP, Math.ceil(rps * 0.55)));
  const rawMax = Math.max(pre + 1000, Math.ceil(rps * 10));
  const max = Math.min(VU_MAX_CAP, rawMax);
  return { preAllocatedVUs: pre, maxVUs: max };
}

const b = vuBudget(RPS_PER_PORT);

export const options = {
  scenarios: {
    lane_1: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane1',
      tags: { lane: '1', target: 'baseline' },
    },
    lane_2: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane2',
      tags: { lane: '2', target: 'baseline' },
    },
    lane_3: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane3',
      tags: { lane: '3', target: 'baseline' },
    },
  },
  thresholds: buildHttpThresholds(),
  summaryTrendStats: SUMMARY_TREND_STATS,
};

const PATH = '/api/order/snag';

function postSnag(baseUrl, portTag) {
  const body = JSON.stringify({
    userId: `k6_${portTag}_${__VU}_${__ITER}_${Date.now()}`,
    productId: BASELINE_PRODUCT_ID,
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
