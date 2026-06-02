/**
 * 在 compose 默认网络内压测，不经 Windows 宿主端口转发。
 * 默认 `ORDER_BASE=http://singularity-order:8081`：三条车道同一服务名，依赖 Docker DNS/连接分散，**不保证**均衡落到每一副本。
 * 若已起 **3 个 order 副本** 且要 **确保三车道各打一台**，请设 **ORDER_TARGETS**（3 个无 path 的基址，逗号分隔），例如三台容器名：
 *   ORDER_TARGETS=http://singularity-singularity-order-7:8081,http://singularity-singularity-order-8:8081,http://singularity-singularity-order-9:8081
 * 在宿主生成（需先 docker ps 有 ≥3 个 order）：
 *   PowerShell:  $t = .\deploy\k6-print-order-targets.ps1; docker compose ... run --rm -e ORDER_TARGETS=$t k6-order-load
 *   Bash:        t=$(./tests/order-stress-test/k6-print-order-targets.sh); docker compose ... run --rm -e ORDER_TARGETS=$t k6-order-load
 *
 *   cd deploy
 *   docker compose -f docker-compose.backend.yml --profile k6 run --rm k6-order-load
 */
import http from 'k6/http';
import { check } from 'k6';
import { postJsonParams } from './k6-http-params.js';
import { SUMMARY_TREND_STATS, buildHttpThresholds } from './k6-snag-options.js';
import { textSummary } from './k6-summary-0.0.1.js';

const RPS_PER_PORT = Number(__ENV.RPS_PER_PORT || 4500);
const DURATION = __ENV.DURATION || '2m';
/** compose 默认同挂载 tests/order-stress-test/k6-out → /out，结果在宿主 k6-out/summary-docker.json */
const SUMMARY_OUT = __ENV.SUMMARY_OUT || '/out/summary-docker.json';
const VU_PRE_CAP = Number(__ENV.VU_PRE_CAP || 12000);
const VU_MAX_CAP = Number(__ENV.VU_MAX_CAP || 80000);

const ORDER_BASE = __ENV.ORDER_BASE || 'http://singularity-order:8081';

/**
 * 三车道基址：ORDER_TARGETS 优先（须正好 3 个 URL），否则三条车道均用 ORDER_BASE。
 */
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
      tags: { lane: '1', target: 'docker-internal' },
    },
    lane_2: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane2',
      tags: { lane: '2', target: 'docker-internal' },
    },
    lane_3: {
      executor: 'constant-arrival-rate',
      rate: RPS_PER_PORT,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: b.preAllocatedVUs,
      maxVUs: b.maxVUs,
      exec: 'hitLane3',
      tags: { lane: '3', target: 'docker-internal' },
    },
  },
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
