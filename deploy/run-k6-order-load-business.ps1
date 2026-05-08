param(
    [string]$Duration = "1m",
    [string]$RpsPerPort = "4500",
    [string]$ComposeFile = "docker-compose.backend.yml",
    [string]$StrictBusinessMinRate = ""
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
# k6-snag-docker-internal-business.js -> k6-out/summary-docker-business.json
# Optional: -StrictBusinessMinRate 0.05 (with Redis refilled)
$strict = $StrictBusinessMinRate -and $StrictBusinessMinRate.Trim().Length -gt 0
if ($strict) {
    docker compose -f $ComposeFile --profile k6 run --rm --no-deps `
        -e "DURATION=$Duration" `
        -e "RPS_PER_PORT=$RpsPerPort" `
        -e "STRICT_BUSINESS_MIN_RATE=$StrictBusinessMinRate" `
        k6-order-load-business
} else {
    docker compose -f $ComposeFile --profile k6 run --rm --no-deps `
        -e "DURATION=$Duration" `
        -e "RPS_PER_PORT=$RpsPerPort" `
        k6-order-load-business
}
