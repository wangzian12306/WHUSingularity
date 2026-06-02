# Via singularity-order-lb-1/2/3 (nginx) inside compose; k6 各车道打一实例。Use --no-deps so compose does not reconcile order replica count.
# Example: .\run-k6-order-lb-load.ps1 -Duration 2m
# Example: .\run-k6-order-lb-load.ps1 -Duration 2m -RpsPerPort 2500
param(
    [string]$Duration = '2m',
    [string]$RpsPerPort = '4000',
    [string]$ComposeFile = 'docker-compose.backend.yml'
)
$ErrorActionPreference = 'Stop'
$DeployDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\deploy')).Path
Set-Location $DeployDir
docker compose -f $ComposeFile --profile k6 run --rm --no-deps `
    -e "DURATION=$Duration" `
    -e "RPS_PER_PORT=$RpsPerPort" `
    k6-order-lb-load
