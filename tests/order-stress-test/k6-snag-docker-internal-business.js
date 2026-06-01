/**
 * 与 k6-snag-docker-internal.js 相同拓扑，另增加「业务成功」统计：校验 JSON body 中 success === true
 *（与 HTTP 200 区分；失败抢单接口仍常返回 200 + success:false）。
 *
 * 指标：自定义 Rate `snag_business_success`（报告中为通过率）；checks 含 `business success`。
 * 可选环境变量 STRICT_BUSINESS_MIN_RATE：若设为 0.01 等，则在 order-topic 应有消息前请先 refill Redis），否则 threshold 会失败。
 *
 * Docker（与 k6-order-load 相同依赖，输出 summary-docker-business.json）：
 *   cd deploy
 *   docker compose -f docker-compose.backend.yml --profile k6 run --rm --no-deps k6-order-load-business
 * 或：.\run-k6-order-load-business.cmd -Duration 1m
 */
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const snagBusinessSuccess = new Rate('snag_business_success');

const RPS_PER_PORT = Number(__ENV.RPS_PER_PORT || 4500);
const DURATION = __ENV.DURATION || '2m';
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '/out/summary-docker-business.json';
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
        `ORDER_TARGETS 必须恰好 3 个基址（无 path），当前 ${parts.length} 个。示例：` +
          `http://singularity-singularity-order-7:8081,http://...`,
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

const thresholds = buildHttpThresholds();
const strictRate = __ENV.STRICT_BUSINESS_MIN_RATE;
if (strictRate !== undefined && String(strictRate).trim() !== '') {
  thresholds['snag_business_success'] = [`rate>${String(strictRate).trim()}`];
}

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
      tags: { lane: '1', target: 'docker-internal-business' },
    },
    lane_2: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane2',
      tags: { lane: '2', target: 'docker-internal-business' },
    },
    lane_3: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane3',
      tags: { lane: '3', target: 'docker-internal-business' },
    },
  },
  thresholds,
  summaryTrendStats: SUMMARY_TREND_STATS,
};

const PATH = '/api/order/snag';

function snagResponseBusinessOk(res) {
  if (res.status !== 200 || !res.body) {
    return false;
  }
  try {
    const j = res.json();
    return j && j.success === true;
  } catch (_) {
    return false;
  }
}

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
  const businessOk = snagResponseBusinessOk(res);
  snagBusinessSuccess.add(businessOk);
  check(res, {
    [`${portTag} status 200`]: (r) => r.status === 200,
    [`${portTag} business success`]: () => businessOk,
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
