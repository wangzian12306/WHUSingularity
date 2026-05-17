#!/bin/sh
set -e
if ! command -v curl >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y -qq curl ca-certificates
fi
DOCKER_CLI_VER="${DOCKER_CLI_VER:-27.4.0}"
COMPOSE_VER="${COMPOSE_VER:-2.29.7}"

if ! command -v docker >/dev/null 2>&1; then
  ARCH=$(uname -m)
  case "$ARCH" in
    x86_64) DARCH=x86_64 ;;
    aarch64|arm64) DARCH=aarch64 ;;
    *) DARCH=x86_64 ;;
  esac
  mkdir -p /tmp/docker-static
  curl -fsSL "https://download.docker.com/linux/static/stable/${DARCH}/docker-${DOCKER_CLI_VER}.tgz" \
    | tar xz -C /tmp/docker-static
  mv /tmp/docker-static/docker/docker /usr/local/bin/docker
  chmod +x /usr/local/bin/docker
fi

if ! docker compose version >/dev/null 2>&1; then
  mkdir -p /usr/local/lib/docker/cli-plugins
  ARCH=$(uname -m)
  case "$ARCH" in
    x86_64) BIN=docker-compose-linux-x86_64 ;;
    aarch64|arm64) BIN=docker-compose-linux-aarch64 ;;
    *) BIN=docker-compose-linux-x86_64 ;;
  esac
  curl -fsSL "https://github.com/docker/compose/releases/download/v${COMPOSE_VER}/${BIN}" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi

exec java -jar singularity-scaler/target/singularity-scaler-1.0-SNAPSHOT.jar
