param(
    [string]$RpsRamp = '500,1000,1500,2000,2500',
    [string]$StepSec = '60',
    [string]$GatewayBase = 'http://singularity-gateway:8080',
    [string]$VuMaxCap = '4000',
    [string]$VuPreCap = '800',
    [string]$ComposeFile = 'docker-compose.backend.yml',
    [switch]$CheckBusiness
)

# Gateway + allocate（仅 userId）→ FraudDetectionInterceptor + 拦截器链 + snag。
# 单车道总 RPS 阶梯（对齐作者 ~1500 RPS/实例 CPU 演示），不经 ORDER_TARGETS 直连 order。
#
# Prereq:
#   docker compose -f docker-compose.backend.yml up -d singularity-gateway singularity-order
#   （可选 scale order=3；scaler 演示可先 1 实例）
#   tests/order-stress-test/refill-stock-buckets.ps1
#   order 环境变量示例（compose / Nacos）：
#     SINGULARITY_ORDER_FRAUD_DETECTION_ENABLED=true
#     SINGULARITY_ORDER_FRAUD_DETECTION_HASH_ROUNDS=8
#     SINGULARITY_ORDER_FRAUD_DETECTION_MAX_REQUESTS_PER_WINDOW=10000
#
# 观测：
#   docker stats singularity-gateway-0 singularity-singularity-order-1
#   结果：tests/order-stress-test/k6-out/summary-gateway-fraud-ramp.json

$ErrorActionPreference = 'Stop'
$DeployDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\deploy')).Path
Set-Location $DeployDir

$dockerArgs = @(
    '-f', $ComposeFile,
    '--profile', 'k6', 'run', '--rm', '--no-deps',
    '-e', "ORDER_BASE=$GatewayBase",
    '-e', "RPS_RAMP=$RpsRamp",
    '-e', "STEP_SEC=$StepSec",
    '-e', "VU_MAX_CAP=$VuMaxCap",
    '-e', "VU_PRE_CAP=$VuPreCap",
    '-e', 'SUMMARY_OUT=/out/summary-gateway-fraud-ramp.json'
)
if ($CheckBusiness) {
    $dockerArgs += '-e', 'CHECK_BUSINESS=1'
}

$totalSec = ($RpsRamp.Split(',').Count) * [int]$StepSec
Write-Host "Gateway fraud ramp: ORDER_BASE=$GatewayBase"
Write-Host "RPS_RAMP=$RpsRamp (total RPS per step, single lane)"
Write-Host "STEP_SEC=$StepSec x $($RpsRamp.Split(',').Count) steps ≈ ${totalSec}s"
Write-Host "Body: { userId } only → allocate + FraudDetection (no productId)"

$dockerArgs += 'k6-order-gateway-fraud-ramp'
& docker compose @dockerArgs
