# 全量迁移：拆掉旧 compose 项目（含默认项目名 deploy）、再以 name: singularity 重建所有服务容器。
# 在仓库根执行:  powershell -ExecutionPolicy Bypass -File deploy/rebuild-stack.ps1
# 清空 MySQL/Maven 命名卷: 同上附加 -RemoveVolumes
param(
    [switch]$RemoveVolumes,
    [ValidateSet('none', 'old', 'all')]
    [string]$RemoveNamedVolumes = 'none'
)

# docker compose 常把进度写到 stderr，勿用 Stop，否则会误当作异常终止
$ErrorActionPreference = 'Continue'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$composeFile = Join-Path $scriptDir 'docker-compose.backend.yml'
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..')).Path
$env:REPO_ROOT = $repoRoot

Write-Host '==> [1/4] 停止旧项目 deploy（未写 name: 时在 deploy 目录 up 的默认项目名）...' -ForegroundColor Cyan
Push-Location $repoRoot
try {
    docker compose -f $composeFile -p deploy down --remove-orphans 2>$null
}
finally {
    Pop-Location
}

Write-Host '==> [2/4] 停止当前 compose 文件对应项目（name: singularity）...' -ForegroundColor Cyan
Push-Location $repoRoot
try {
    if ($RemoveVolumes) {
        docker compose -f $composeFile down -v --remove-orphans 2>$null
    }
    else {
        docker compose -f $composeFile down --remove-orphans 2>$null
    }
}
finally {
    Pop-Location
}

if ($RemoveNamedVolumes -eq 'old' -or $RemoveNamedVolumes -eq 'all') {
    Write-Host '==> [2b] 尝试删除旧项目遗留命名卷（deploy_*）...' -ForegroundColor Yellow
    docker volume rm deploy_mysql_data deploy_maven_repo 2>$null
}
if ($RemoveNamedVolumes -eq 'all') {
    Write-Host '==> [2c] 删除当前项目命名卷（singularity_*，数据库与 m2 缓存将丢失）...' -ForegroundColor Yellow
    docker volume rm singularity_mysql_data singularity_maven_repo 2>$null
}

Write-Host '==> [3/4] 删除名称含 singularity 的旧容器（解除与其它项目的 container_name 冲突）...' -ForegroundColor Cyan
docker ps -aq -f "name=singularity" | ForEach-Object { docker rm -f $_ 2>$null }

Write-Host '==> [4/4] 重建并启动（order=3 副本 + front build）...' -ForegroundColor Cyan
Push-Location $repoRoot
try {
    docker compose -f $composeFile up -d --build --force-recreate --remove-orphans --scale singularity-order=3
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'docker compose up failed'
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}

Write-Host '[DONE] 状态:' -ForegroundColor Green
Push-Location $repoRoot
try {
    docker compose -f $composeFile ps -a
}
finally {
    Pop-Location
}
