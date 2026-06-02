/**
 * 单车道 constant-arrival-rate 压测（仅 1 个 scenario，不再使用 lane_1/2/3）。
 * ORDER_BASE：单实例基址（无 path），与 run-k6-order-load-one / run-k6-order-baseline-load 配合。
 * RPS：优先 __ENV.RPS，否则 __ENV.RPS_PER_PORT，默认 12000（单实例总目标）。
 * order：设 ORDER_PRODUCT_ID（如 PROD_001）→ snagOrderByProduct，与 baseline 一样带 productId。
 * baseline：设 BASELINE_PRODUCT_ID（如 1001）→ 同上，走 baseline 服务。
 */
import http from 'k6/http';
import { check } from 'k6';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const RPS = Number(__ENV.RPS || __ENV.RPS_PER_PORT || 12000);
const DURATION = __ENV.DURATION || '2m';
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '/out/summary-docker-single-lane.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 12000);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 80000);

const ORDER_BASE = (__ENV.ORDER_BASE || 'http://singularity-order:8081').replace(/\/$/, '');
const BASELINE_RAW = __ENV.BASELINE_PRODUCT_ID;
const ORDER_PRODUCT_RAW = __ENV.ORDER_PRODUCT_ID;
const IS_BASELINE =
  BASELINE_RAW !== undefined && BASELINE_RAW !== null && String(BASELINE_RAW).trim() !== '';
const ORDER_PRODUCT_ID =
  !IS_BASELINE && ORDER_PRODUCT_RAW !== undefined && ORDER_PRODUCT_RAW !== null
    ? String(ORDER_PRODUCT_RAW).trim()
    : '';

function vuBudget(rps) {
  const pre = Math.max(500, Math.min(VU_PRE_CAP, Math.ceil(rps * 0.55)));
  const rawMax = Math.max(pre + 1000, Math.ceil(rps * 10));
  const max = Math.min(VU_MAX_CAP, rawMax);
  return { preAllocatedVUs: pre, maxVUs: max };
}

const vb = vuBudget(RPS);

export const options = {
  scenarios: {
    snag_single: {
      executor: 'constant-arrival-rate',
      rate: RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: vb.preAllocatedVUs,
      maxVUs: vb.maxVUs,
      exec: 'hit',
      tags: { target: 'docker-internal-single-lane' },
    },
  },
  thresholds: buildHttpThresholds(),
  summaryTrendStats: SUMMARY_TREND_STATS,
};

const PATH = '/api/order/snag';

export function hit() {
  const userId = `k6_single_${__VU}_${__ITER}_${Date.now()}`;
  let body;
  if (IS_BASELINE) {
    body = JSON.stringify({
      userId,
      productId: String(BASELINE_RAW).trim(),
    });
  } else if (ORDER_PRODUCT_ID) {
    body = JSON.stringify({ userId, productId: ORDER_PRODUCT_ID });
  } else {
    body = JSON.stringify({ userId });
  }

  const res = http.post(
    `${ORDER_BASE}${PATH}`,
    body,
    postJsonParams({
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    }),
  );
  check(res, {
    'status 200': (r) => r.status === 200,
  });
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: false }),
    [SUMMARY_OUT]: JSON.stringify(data, null, 2),
  };
}
