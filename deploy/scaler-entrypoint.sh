#!/bin/sh
# Install curl if missing (non-fatal)
if ! command -v curl >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y -qq curl ca-certificates || true
fi
DOCKER_CLI_VER="${DOCKER_CLI_VER:-27.4.0}"
COMPOSE_VER="${COMPOSE_VER:-2.29.7}"

# Try to install Docker CLI (non-fatal: monitoring panel works without it)
if ! command -v docker >/dev/null 2>&1; then
  ARCH=$(uname -m)
  case "$ARCH" in
    x86_64) DARCH=x86_64 ;;
    aarch64|arm64) DARCH=aarch64 ;;
    *) DARCH=x86_64 ;;
  esac
  echo "[scaler] Attempting to download Docker CLI..."
  if curl -fsSL --connect-timeout 10 --max-time 120 "https://download.docker.com/linux/static/stable/${DARCH}/docker-${DOCKER_CLI_VER}.tgz" \
    | tar xz -C /tmp; then
    mv /tmp/docker/docker /usr/local/bin/docker 2>/dev/null || true
    chmod +x /usr/local/bin/docker 2>/dev/null || true
    echo "[scaler] Docker CLI installed."
  else
    echo "[scaler] WARNING: Failed to download Docker CLI. Scaling features will be unavailable, but monitoring panel will still work."
  fi
fi

# Try to install Docker Compose plugin (non-fatal)
if command -v docker >/dev/null 2>&1 && ! docker compose version >/dev/null 2>&1; then
  mkdir -p /usr/local/lib/docker/cli-plugins
  ARCH=$(uname -m)
  case "$ARCH" in
    x86_64) BIN=docker-compose-linux-x86_64 ;;
    aarch64|arm64) BIN=docker-compose-linux-aarch64 ;;
    *) BIN=docker-compose-linux-x86_64 ;;
  esac
  echo "[scaler] Attempting to download Docker Compose plugin..."
  if curl -fsSL --connect-timeout 10 --max-time 120 "https://github.com/docker/compose/releases/download/v${COMPOSE_VER}/${BIN}" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose; then
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    echo "[scaler] Docker Compose plugin installed."
  else
    echo "[scaler] WARNING: Failed to download Docker Compose plugin. Scaling features will be unavailable, but monitoring panel will still work."
  fi
fi

echo "[scaler] Starting Scaler service..."
exec java -jar singularity-scaler/target/singularity-scaler-1.0-SNAPSHOT.jar
