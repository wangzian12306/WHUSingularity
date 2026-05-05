#!/bin/sh
set -e
cd /workspace
JAR=singularity-order/target/singularity-order-1.0-SNAPSHOT.jar
if [ ! -f "$JAR" ]; then
  echo "FATAL: 未找到 $JAR"
  echo "1) 在仓库根执行: mvn -pl singularity-order -am package -DskipTests"
  echo "2) 在 deploy/.env 设置 REPO_ROOT 为 WHUSingularity 绝对路径（Windows 用 D:/... 正斜杠）"
  echo "----- /workspace 列表 -----"
  ls -la
  echo "----- singularity-order 目录 -----"
  ls -la singularity-order 2>/dev/null || echo "(无此目录，说明卷未挂到仓库根)"
  exit 1
fi
exec java -jar "$JAR"
