/**
 * 在 compose 默认网络内经 **nginx order-lb** 压 order（lb:80 → proxy 到 singularity-order:8081）。
 * 与直连 order 相比：副本变化时仍访问稳定的服务名，适合 Scaler 伸缩下 avoid 固定容器 URL。
 *
 * 默认三台独立 LB（分散单 nginx 进程瓶颈）：LB_TARGETS 逗号分隔三条 URL，车道 1/2/3 各打一台；
 * 未设 LB_TARGETS 时用 LB_BASE / ORDER_BASE 三车道同址；再缺省为 singularity-order-lb-1..3。
 * 总目标 RPS = 3 * RPS_PER_PORT（每车道 constant-arrival-rate）。
 *
 * compose（务必 --no-deps，避免 run 协调掉 order 副本）：
 *   cd deploy
 *   docker compose -f deploy/docker-compose.backend.yml --profile k6 run --rm --no-deps k6-order-lb-load
 * 或宿主：
 *   .\deploy\run-k6-order-lb-load.cmd -Duration 2m
 * 若大量 EOF / server closed idle connection：试 -e NO_CONNECTION_REUSE=1（减轻连接复用与 LB 侧策略不一致）。
 */
import http from 'k6/http';
import { check } from 'k6';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const RPS_PER_PORT = Number(__ENV.RPS_PER_PORT || 6000);
const DURATION = __ENV.DURATION || '2m';
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '/out/summary-docker-lb.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 12000);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 80000);

function resolveLaneBases() {
  const targets = (__ENV.LB_TARGETS || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((u) => u.replace(/\/$/, ''));
  const single = (
    __ENV.LB_BASE ||
    __ENV.ORDER_BASE ||
    ''
  ).replace(/\/$/, '');
  if (targets.length >= 3) return [targets[0], targets[1], targets[2]];
  if (targets.length === 1) return [targets[0], targets[0], targets[0]];
  if (targets.length === 2) return [targets[0], targets[1], targets[1]];
  if (single) return [single, single, single];
  return [
    'http://singularity-order-lb-1',
    'http://singularity-order-lb-2',
    'http://singularity-order-lb-3',
  ];
}

const BASES = resolveLaneBases();

/** 设为 1/true 时每请求新连接，可减轻「server closed idle connection」类错误（代价是更多新建连接） */
const NO_CONNECTION_REUSE =
  __ENV.NO_CONNECTION_REUSE === '1' || __ENV.NO_CONNECTION_REUSE === 'true';

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
      tags: { lane: '1', target: 'order-lb' },
    },
    lane_2: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane2',
      tags: { lane: '2', target: 'order-lb' },
    },
    lane_3: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane3',
      tags: { lane: '3', target: 'order-lb' },
    },
  },
  thresholds: buildHttpThresholds(),
  summaryTrendStats: SUMMARY_TREND_STATS,
};

const PATH = '/api/order/snag';

function postSnag(baseUrl, portTag) {
  const body = JSON.stringify({
    userId: `k6_lb_${portTag}_${__VU}_${__ITER}_${Date.now()}`,
  });
  const res = http.post(
    `${baseUrl}${PATH}`,
    body,
    postJsonParams({
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      tags: { lane: portTag },
      noConnectionReuse: NO_CONNECTION_REUSE,
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
