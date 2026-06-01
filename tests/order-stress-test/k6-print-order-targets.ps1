# Print comma-separated http://<container>:8081 for first N singularity-order replicas (docker ps, sorted by name).
# Example: $t = .\k6-print-order-targets.ps1; docker compose ... -e "ORDER_TARGETS=$t" k6-order-load
param(
    [int]$Count = 3
)
$ErrorActionPreference = "Stop"
$arr = @(
    docker ps --filter "label=com.docker.compose.service=singularity-order" --format "{{.Names}}" |
        Where-Object { $_ } |
        Sort-Object
)
if ($arr.Count -lt $Count) {
    Write-Error "Need $Count singularity-order containers, got $($arr.Count). Run: docker compose -f deploy/docker-compose.backend.yml up -d --scale singularity-order=$Count"
    exit 1
}
(0..($Count - 1) | ForEach-Object { "http://$($arr[$_]):8081" }) -join ","
