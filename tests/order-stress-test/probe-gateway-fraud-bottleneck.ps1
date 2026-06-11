param(
    [string]$Rps = '2500',
    [string]$DurationSec = '30',
    [string]$ComposeFile = 'docker-compose.backend.yml',
    [int]$StatsIntervalSec = 3
)

# 对比 gateway / order / redis 在 fraud ramp 路径下的 CPU，定位链路瓶颈（给老师汇报用）。
# 用法：.\probe-gateway-fraud-bottleneck.ps1 -Rps 2000 -DurationSec 45

$ErrorActionPreference = 'Stop'
$DeployDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..\deploy')).Path
Set-Location $DeployDir

$outDir = Join-Path $PSScriptRoot 'k6-out'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$statsFile = Join-Path $outDir 'bottleneck-docker-stats.txt'
$summaryFile = Join-Path $outDir 'summary-bottleneck-probe.json'

$containers = @(
    'singularity-gateway-0',
    'singularity-singularity-order-3',
    'singularity-singularity-order-5',
    'singularity-redis',
    'singularity-rmq-broker'
)

$sampleCount = [Math]::Ceiling([int]$DurationSec / $StatsIntervalSec) + 2
Write-Host "Probe: ${Rps} RPS x ${DurationSec}s, sampling docker stats every ${StatsIntervalSec}s -> $statsFile"

$statsJob = Start-Job -ArgumentList @($statsFile, $containers, $StatsIntervalSec, $sampleCount) -ScriptBlock {
    param($path, $names, $interval, $n)
    Remove-Item $path -ErrorAction SilentlyContinue
    for ($i = 0; $i -lt $n; $i++) {
        $line = (Get-Date -Format 'HH:mm:ss') + ' UTC'
        Add-Content $path $line
        docker stats @names --no-stream --format '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}' | Add-Content $path
        Add-Content $path '---'
        Start-Sleep $interval
    }
}

Start-Sleep -Seconds 1
docker compose -f $using:ComposeFile --profile k6 run --rm --no-deps `
    -e 'ORDER_BASE=http://singularity-gateway:8080' `
    -e "RPS_RAMP=$using:Rps" `
    -e "STEP_SEC=$using:DurationSec" `
    -e "SUMMARY_OUT=/out/summary-bottleneck-probe.json" `
    k6-order-gateway-fraud-ramp | Out-Host

Wait-Job $statsJob | Out-Null
Receive-Job $statsJob | Out-Null
Remove-Job $statsJob

Write-Host "`n=== docker stats (load samples, skip first block) ==="
$blocks = (Get-Content $statsFile -Raw) -split '---'
$loadBlocks = $blocks | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne '' }
foreach ($b in $loadBlocks) {
    Write-Host $b.Trim()
    Write-Host '---'
}

Write-Host "`nInterpretation:"
Write-Host "  gateway CPU >> order CPU  -> 扩 gateway（当前 compose 仅 1 实例 singularity-gateway-0）"
Write-Host "  order CPU ~100% x N 副本 -> 扩 singularity-order（FraudDetection CPU 密集）"
Write-Host "  redis / rmq CPU 持续很低 -> 不是瓶颈"
