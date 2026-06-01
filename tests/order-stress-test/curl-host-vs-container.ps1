# 容器内直连某一 order 副本的 8081（多副本时任选一个容器 ID）。
# 宿主侧请对比：Invoke-WebRequest http://localhost:8081/...（经 singularity-order-lb）
$ErrorActionPreference = "Stop"
$project = "singularity"
$svc = "singularity-order"
$id = docker ps -qf "label=com.docker.compose.project=$project" -f "label=com.docker.compose.service=$svc" | Select-Object -First 1
if (-not $id) { throw "No singularity-order container found (project=$project)." }

docker exec $id sh -c 'for i in 1 2 3 4 5; do curl -s -o /dev/null -w "in-order http=%{http_code} total=%{time_total}s\n" -X POST http://127.0.0.1:8081/api/order/snag -H "Content-Type: application/json" -d "{\"userId\":\"in-ctr-$i\"}"; done'
