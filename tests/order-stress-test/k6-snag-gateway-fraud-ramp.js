/**
 * Gateway + allocate（仅 userId）+ FraudDetection 阶梯 RPS 压测。
 * 对齐 ATOMLubover：单车道总 RPS（非 3×），经 gateway 分流到 order，走拦截器链。
 *
 * 环境变量：
 *   ORDER_BASE   默认 http://singularity-gateway:8080
 *   RPS_RAMP     逗号分隔总 RPS 台阶，默认 500,1000,1500,2000,2500
 *   STEP_SEC     每档秒数，默认 60
 *   SUMMARY_OUT  默认 /out/summary-gateway-fraud-ramp.json
 *   VU_PRE_CAP / VU_MAX_CAP
 *   CHECK_BUSINESS  设为 1 时校验 JSON success===true（风控拦截会拉低通过率）
 *
 * 压测前：refill-stock-buckets；order 建议设 fraud-detection.hash-rounds=8；
 *   高压下可调大 max-requests-per-window，避免误杀。
 * 观测：docker stats singularity-gateway-0 + singularity-order；scaler 可选。
 */
import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const snagBusinessSuccess = new Rate('snag_business_success');

const STEP_SEC = Number(__ENV.STEP_SEC || 60);
const RPS_RAMP = __ENV.RPS_RAMP
  ? __ENV.RPS_RAMP.split(',').map((s) => Number(s.trim()))
  : [500, 1000, 1500, 2000, 2500];
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '/out/summary-gateway-fraud-ramp.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 800);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 4000);
const CHECK_BUSINESS = String(__ENV.CHECK_BUSINESS || '0').trim() === '1';

const ORDER_BASE = (__ENV.ORDER_BASE || 'http://singularity-gateway:8080').replace(/\/$/, '');
const PATH = '/api/order/snag';

function vuBudget(rps) {
  const pre = Math.max(200, Math.min(VU_PRE_CAP, Math.ceil(rps * 0.55)));
  const rawMax = Math.max(pre + 200, Math.ceil(rps * 4));
  const max = Math.min(VU_MAX_CAP, rawMax);
  return { preAllocatedVUs: pre, maxVUs: max };
}

const maxRps = Math.max(...RPS_RAMP);
const vb = vuBudget(maxRps);
const stepDuration = `${STEP_SEC}s`;

function buildScenarios() {
  const scenarios = {};
  for (let i = 0; i < RPS_RAMP.length; i++) {
    const rps = RPS_RAMP[i];
    scenarios[`fraud_ramp_${i + 1}`] = {
      executor: 'constant-arrival-rate',
      rate: rps,
      timeUnit: '1s',
      duration: stepDuration,
      startTime: `${i * STEP_SEC}s`,
      preAllocatedVUs: vb.preAllocatedVUs,
      maxVUs: vb.maxVUs,
      exec: 'hit',
      tags: {
        target: 'gateway-fraud-ramp',
        ramp_step: String(i + 1),
        rps: String(rps),
      },
    };
  }
  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: buildHttpThresholds({ failRate: 0.15 }),
  summaryTrendStats: SUMMARY_TREND_STATS,
};

export function hit() {
  const userId = `k6_fraud_${__VU}_${__ITER}_${Date.now()}`;
  const body = JSON.stringify({ userId });
  const res = http.post(
    `${ORDER_BASE}${PATH}`,
    body,
    postJsonParams({
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    }),
  );

  check(res, { 'status 200': (r) => r.status === 200 });

  let businessOk = false;
  if (res.status === 200 && res.body) {
    try {
      const json = JSON.parse(res.body);
      businessOk = json.success === true;
    } catch (_) {
      businessOk = false;
    }
  }
  snagBusinessSuccess.add(businessOk);
  if (CHECK_BUSINESS) {
    check(res, { 'business success': () => businessOk });
  }
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: false }),
    [SUMMARY_OUT]: JSON.stringify(data, null, 2),
  };
}
