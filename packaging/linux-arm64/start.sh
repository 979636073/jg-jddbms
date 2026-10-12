#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

[[ "$(uname -s)" == Linux && "$(uname -m)" == aarch64 ]] || { echo '本包仅支持 Linux ARM64/aarch64。' >&2; exit 1; }
for command_name in java curl; do
  command -v "$command_name" >/dev/null 2>&1 || { echo "缺少系统命令：$command_name" >&2; exit 1; }
done
if [[ -f "$ROOT/jd-dbms-admin.jar" ]]; then
  JAR_FILE="$ROOT/jd-dbms-admin.jar"
  WEB_INDEX="$ROOT/dist/index.html"
else
  JAR_FILE="$ROOT/app/jddbms.jar"
  WEB_INDEX="$ROOT/web/index.html"
fi
if [[ -f "$ROOT/redis/bin/redis-server" ]]; then
  REDIS_SERVER="$ROOT/redis/bin/redis-server"
  REDIS_CLI="$ROOT/redis/bin/redis-cli"
elif [[ -f "$ROOT/redis/redis-server" ]]; then
  REDIS_SERVER="$ROOT/redis/redis-server"
  REDIS_CLI="$ROOT/redis/redis-cli"
else
  command -v redis-server >/dev/null 2>&1 || { echo '缺少 Redis 程序。' >&2; exit 1; }
  command -v redis-cli >/dev/null 2>&1 || { echo '缺少 Redis 客户端。' >&2; exit 1; }
  REDIS_SERVER="$(command -v redis-server)"
  REDIS_CLI="$(command -v redis-cli)"
fi
if [[ "$REDIS_SERVER" == "$ROOT"/* ]]; then
  chmod 700 "$REDIS_SERVER"
  [[ ! -f "$REDIS_CLI" ]] || chmod 700 "$REDIS_CLI"
fi
[[ -f config/local.env ]] || { echo '请先复制 config/local.example.env 为 config/local.env 并填写配置。' >&2; exit 1; }
[[ -f "$JAR_FILE" && -f "$WEB_INDEX" ]] || { echo '应用文件不完整。' >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source config/local.env
set +a
if [[ -n "${JDDBMS_NGINX_BIN:-}" ]]; then
  NGINX_BIN="$JDDBMS_NGINX_BIN"
  [[ -x "$NGINX_BIN" ]] || { echo "Nginx 程序不可执行：$NGINX_BIN" >&2; exit 1; }
else
  command -v nginx >/dev/null 2>&1 || { echo '缺少 Nginx；请安装或设置 JDDBMS_NGINX_BIN。' >&2; exit 1; }
  NGINX_BIN="$(command -v nginx)"
fi
for variable_name in JDDBMS_META_DB_URL JDDBMS_META_DB_USERNAME JDDBMS_META_DB_PASSWORD JDDBMS_TOKEN_SECRET JDDBMS_DATASOURCE_ENCRYPTION_KEY; do
  [[ -n "${!variable_name:-}" ]] || { echo "配置缺少 $variable_name。" >&2; exit 1; }
done
REDIS_HOST="${JDDBMS_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${JDDBMS_REDIS_PORT:-6379}"
BACKEND_PORT="${JDDBMS_BACKEND_PORT:-28080}"
[[ "$REDIS_PORT" =~ ^[0-9]+$ && "$REDIS_PORT" -ge 1 && "$REDIS_PORT" -le 65535 ]] \
  || { echo 'JDDBMS_REDIS_PORT 必须是 1–65535 之间的端口号。' >&2; exit 1; }
[[ "$BACKEND_PORT" =~ ^[0-9]+$ && "$BACKEND_PORT" -ge 1 && "$BACKEND_PORT" -le 65535 ]] \
  || { echo 'JDDBMS_BACKEND_PORT 必须是 1–65535 之间的端口号。' >&2; exit 1; }
[[ "$BACKEND_PORT" != "$REDIS_PORT" && "$BACKEND_PORT" != 18081 ]] \
  || { echo '后端端口不能与 Redis 或前端端口相同。' >&2; exit 1; }
export SPRING_DEVTOOLS_RESTART_ENABLED=false
mkdir -p run logs temp data/redis data/uploadUrl
chmod 700 run data

port_open() { (echo >/dev/tcp/127.0.0.1/"$1") >/dev/null 2>&1; }
wait_port() {
  local port="$1" attempts="$2"
  for ((i=0; i<attempts; i++)); do port_open "$port" && return 0; sleep 1; done
  return 1
}
wait_http() {
  local url="$1" attempts="$2"
  for ((i=0; i<attempts; i++)); do curl -fsS --max-time 3 -o /dev/null "$url" && return 0; sleep 1; done
  return 1
}

redis_quote() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  printf '"%s"' "$value"
}
redis_ping() {
  REDISCLI_AUTH="${JDDBMS_REDIS_PASSWORD:-}" "$REDIS_CLI" -h "$REDIS_HOST" -p "$REDIS_PORT" ping 2>/dev/null | grep -qx PONG
}

if [[ "$REDIS_HOST" == 127.0.0.1 || "$REDIS_HOST" == localhost ]]; then
  if ! port_open "$REDIS_PORT"; then
    {
      printf 'bind 127.0.0.1\nport %s\nprotected-mode yes\ndaemonize yes\n' "$REDIS_PORT"
      printf 'pidfile %s\n' "$(redis_quote "$ROOT/run/redis.pid")"
      printf 'dir %s\n' "$(redis_quote "$ROOT/data/redis")"
      printf 'logfile %s\nappendonly yes\n' "$(redis_quote "$ROOT/logs/redis.log")"
      if [[ -n "${JDDBMS_REDIS_PASSWORD:-}" ]]; then
        printf 'requirepass %s\n' "$(redis_quote "$JDDBMS_REDIS_PASSWORD")"
      fi
    } >run/redis.conf
    chmod 600 run/redis.conf
    "$REDIS_SERVER" "$ROOT/run/redis.conf"
    wait_port "$REDIS_PORT" 20 || { echo 'Redis 启动失败，请查看 logs/redis.log。' >&2; exit 1; }
  else
    echo "使用已运行的本机 Redis（端口 $REDIS_PORT）。"
  fi
else
  echo "使用外部 Redis：$REDIS_HOST:$REDIS_PORT"
fi
redis_ping || { echo 'Redis 连接或认证失败，请检查 JDDBMS_REDIS_HOST、JDDBMS_REDIS_PORT 和 JDDBMS_REDIS_PASSWORD。' >&2; exit 1; }

[[ -f nginx/nginx.conf.template ]] || { echo '缺少 Nginx 配置模板。' >&2; exit 1; }
nginx_template="$(<nginx/nginx.conf.template)"
[[ "$nginx_template" == *'__JDDBMS_BACKEND_PORT__'* ]] || { echo 'Nginx 配置模板缺少后端端口占位符。' >&2; exit 1; }
printf '%s\n' "${nginx_template//__JDDBMS_BACKEND_PORT__/$BACKEND_PORT}" >nginx/nginx.conf

if ! port_open "$BACKEND_PORT"; then
  nohup java -Dfile.encoding=UTF-8 "-Xms${JDDBMS_JAVA_XMS:-256m}" "-Xmx${JDDBMS_JAVA_XMX:-1024m}" -jar "$JAR_FILE" \
    --server.address=127.0.0.1 --server.port="$BACKEND_PORT" --dbms.profile="$ROOT/data" \
    --spring.redis.port="$REDIS_PORT" \
    --pigenhole.uploadUrl="$ROOT/data/uploadUrl" \
    >"$ROOT/logs/app.out.log" 2>"$ROOT/logs/app.err.log" </dev/null &
  echo "$!" >run/app.pid
  wait_http "http://127.0.0.1:$BACKEND_PORT/captchaImage" 90 || { echo '后端启动失败，请查看 logs/app.*.log。' >&2; exit 1; }
else
  wait_http "http://127.0.0.1:$BACKEND_PORT/captchaImage" 3 || { echo "$BACKEND_PORT 端口已被其他服务占用。" >&2; exit 1; }
  echo '本包后端已在运行。'
fi

if ! port_open 18081; then
  "$NGINX_BIN" -t -p "$ROOT/" -c nginx/nginx.conf
  "$NGINX_BIN" -p "$ROOT/" -c nginx/nginx.conf
  wait_http 'http://127.0.0.1:18081/healthz' 20 || { echo 'Nginx 启动失败，请查看 logs/nginx.error.log。' >&2; exit 1; }
else
  wait_http 'http://127.0.0.1:18081/healthz' 3 || { echo '18081 端口已被其他服务占用。' >&2; exit 1; }
  echo '本包前端已在运行。'
fi

wait_http 'http://127.0.0.1:18081/dev-api/captchaImage' 10 || { echo '前后端代理校验失败。' >&2; exit 1; }
echo '数据库管理平台已启动：http://<本机IP>:18081/'
