param(
    [string]$Duration = '1m',
    [string]$RpsPerPort = '30000',
    [string]$ComposeFile = 'docker-compose.backend.yml'
)

# 三车道各打一台 order（须先 scale singularity-order=3）。
# 脚本 k6-snag-docker-internal.js：body 仅 userId → allocate + 拦截器链（无 productId）。
# 总目标 RPS ≈ 3 × RpsPerPort（每车道各 RpsPerPort）。
# Prereq: docker compose ... up -d --scale singularity-order=3；Redis：tests/order-stress-test/refill-stock-buckets.ps1

$ErrorActionPreference = 'Stop'
$DeployDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\deploy')).Path
Set-Location $DeployDir

$t = & (Join-Path $PSScriptRoot 'k6-print-order-targets.ps1') -Count 3
if (-not $t) { throw 'ORDER_TARGETS empty; need 3 running singularity-order containers.' }

Write-Host "ORDER_TARGETS=$t"
Write-Host "Total RPS (approx) = 3 x $RpsPerPort = $([int]$RpsPerPort * 3)"

docker compose -f $ComposeFile --profile k6 run --rm --no-deps `
    -e "ORDER_TARGETS=$t" `
    -e "DURATION=$Duration" `
    -e "RPS_PER_PORT=$RpsPerPort" `
    k6-order-load
