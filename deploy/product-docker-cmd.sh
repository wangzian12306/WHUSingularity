#!/bin/sh
set -e
cd /workspace
JAR=singularity-product/target/singularity-product-1.0-SNAPSHOT.jar
if [ ! -f "$JAR" ]; then
  echo "FATAL: 未找到 $JAR"
  echo "1) 在仓库根执行: mvn -pl singularity-product -am package -DskipTests"
  echo "2) 在 deploy/.env 设置 REPO_ROOT 为 WHUSingularity 绝对路径（Windows 用 D:/... 正斜杠）"
  echo "----- /workspace 列表 -----"
  ls -la
  echo "----- singularity-product 目录 -----"
  ls -la singularity-product 2>/dev/null || echo "(无此目录，说明卷未挂到仓库根)"
  exit 1
fi
if [ ! -s "$JAR" ]; then
  echo "FATAL: $JAR 存在但大小为 0（挂载或拷贝异常）"
  exit 1
fi
# 非 Spring Boot repackage 产物时 java -jar 会报: no main manifest attribute, in ...jar
if ! jar tf "$JAR" 2>/dev/null | grep -m1 -q '^BOOT-INF/'; then
  echo "FATAL: $JAR 不是 Spring Boot 可执行 fat jar（缺少 BOOT-INF，java -jar 会报 no main manifest attribute）"
  echo "在仓库根执行: mvn -pl singularity-product -am clean package -DskipTests"
  exit 1
fi
exec java -jar "$JAR"
