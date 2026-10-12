#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
if [[ -f config/local.env ]]; then
  # shellcheck disable=SC1091
  source config/local.env
fi
NGINX_BIN="${JDDBMS_NGINX_BIN:-$(command -v nginx || true)}"
if [[ -f "$ROOT/jd-dbms-admin.jar" ]]; then
  JAR_FILE="$ROOT/jd-dbms-admin.jar"
else
  JAR_FILE="$ROOT/app/jddbms.jar"
fi

if [[ -f run/nginx.pid ]]; then
  if [[ -n "$NGINX_BIN" ]]; then
    "$NGINX_BIN" -p "$ROOT/" -c nginx/nginx.conf -s quit || true
  fi
  echo '已请求停止本包 Nginx。'
fi
if [[ -f run/app.pid ]]; then
  pid="$(<run/app.pid)"
  if [[ "$pid" =~ ^[0-9]+$ && -r "/proc/$pid/cmdline" ]] \
      && tr '\0' ' ' <"/proc/$pid/cmdline" | grep -Fq "$JAR_FILE"; then
    kill "$pid"
    echo '已请求停止本包后端。'
  fi
  rm -f run/app.pid
fi
if [[ -f run/redis.pid ]]; then
  pid="$(<run/redis.pid)"
  if [[ "$pid" =~ ^[0-9]+$ && -r "/proc/$pid/comm" ]] \
      && grep -Eq '^redis-server$' "/proc/$pid/comm"; then
    kill "$pid"
    echo '已请求停止本包 Redis。'
  fi
  rm -f run/redis.pid
fi
