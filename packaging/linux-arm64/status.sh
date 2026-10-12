#!/usr/bin/env bash
set -u
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
if [[ -f config/local.env ]]; then
  # shellcheck disable=SC1091
  source config/local.env
fi
redis_host="${JDDBMS_REDIS_HOST:-127.0.0.1}"
redis_port="${JDDBMS_REDIS_PORT:-6379}"
backend_port="${JDDBMS_BACKEND_PORT:-28080}"
check_port() {
  local name="$1" host="$2" port="$3"
  if (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1; then
    echo "$name：运行中（$host:$port）"
  else
    echo "$name：未监听（$host:$port）"
  fi
}
check_port Redis "$redis_host" "$redis_port"
check_port 后端 127.0.0.1 "$backend_port"
check_port 前端 127.0.0.1 18081
if [[ -f redis/bin/redis-cli ]]; then
  redis_cli=redis/bin/redis-cli
elif [[ -f redis/redis-cli ]]; then
  redis_cli=redis/redis-cli
else
  redis_cli=''
fi
if [[ -n "$redis_cli" ]]; then
  chmod 700 "$redis_cli"
  if REDISCLI_AUTH="${JDDBMS_REDIS_PASSWORD:-}" "$redis_cli" -h "$redis_host" -p "$redis_port" ping 2>/dev/null | grep -qx PONG; then
    echo 'Redis 认证：正常'
  else
    echo 'Redis 认证：失败，请检查地址、端口和密码'
  fi
fi
if command -v curl >/dev/null 2>&1; then
  curl -fsS --max-time 5 -o /dev/null 'http://127.0.0.1:18081/dev-api/captchaImage' \
    && echo '前后端访问：正常' || echo '前后端访问：失败'
fi
