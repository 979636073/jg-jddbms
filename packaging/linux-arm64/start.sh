#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
umask 077

[[ "$(uname -s)" == Linux && "$(uname -m)" == aarch64 ]] || { echo '本包仅支持 Linux ARM64/aarch64。' >&2; exit 1; }
for command_name in java; do
  command -v "$command_name" >/dev/null 2>&1 || { echo "缺少系统命令：$command_name" >&2; exit 1; }
done
if [[ -f "$ROOT/jd-dbms-admin.jar" ]]; then
  JAR_FILE="$ROOT/jd-dbms-admin.jar"
  WEB_INDEX="$ROOT/dist/index.html"
else
  JAR_FILE="$ROOT/app/jddbms.jar"
  WEB_INDEX="$ROOT/web/index.html"
fi
REDIS_SERVER="$ROOT/redis/redis-server"
REDIS_CLI="$ROOT/redis/redis-cli"
NGINX_BIN="$ROOT/nginx/nginx"
for runtime_file in "$REDIS_SERVER" "$REDIS_CLI" "$NGINX_BIN"; do
  [[ -f "$runtime_file" ]] || { echo "缺少包内程序：$runtime_file" >&2; exit 1; }
  chmod 700 "$runtime_file"
done
[[ -f config/local.env ]] || cp config/local.example.env config/local.env
chmod 600 config/local.env
[[ -f "$JAR_FILE" && -f "$WEB_INDEX" ]] || { echo '应用文件不完整。' >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source config/local.env
set +a
# 程序路径固定在安装目录，不从 PATH 或配置文件覆盖。
REDIS_SERVER="$ROOT/redis/redis-server"
REDIS_CLI="$ROOT/redis/redis-cli"
NGINX_BIN="$ROOT/nginx/nginx"
REDIS_HOST="${JDDBMS_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${JDDBMS_REDIS_PORT:-6379}"
BACKEND_PORT="${JDDBMS_BACKEND_PORT:-28080}"
FRONTEND_PORT="${JDDBMS_FRONTEND_PORT:-18081}"
source "$ROOT/metadata-db.sh"
for variable_name in REDIS_PORT BACKEND_PORT FRONTEND_PORT; do
  port="${!variable_name}"
  [[ "$port" =~ ^[0-9]+$ && "$port" -ge 1 && "$port" -le 65535 ]] \
    || { echo "$variable_name 必须是 1–65535 之间的端口号。" >&2; exit 1; }
done
[[ "$BACKEND_PORT" != "$REDIS_PORT" && "$BACKEND_PORT" != "$FRONTEND_PORT" && "$REDIS_PORT" != "$FRONTEND_PORT" ]] \
  || { echo '后端、Redis、前端端口不能相同。' >&2; exit 1; }
export SPRING_DEVTOOLS_RESTART_ENABLED=false
JDDBMS_DATA_DIR="${JDDBMS_DATA_DIR:-$ROOT/data/h2}"
[[ "$JDDBMS_DATA_DIR" == /* ]] || JDDBMS_DATA_DIR="$ROOT/$JDDBMS_DATA_DIR"
export JDDBMS_DATA_DIR
mkdir -p run logs temp data/redis data/uploadUrl "$JDDBMS_DATA_DIR"
chmod 700 run data "$JDDBMS_DATA_DIR"

SECRET_FILE="$ROOT/data/.secrets/runtime.env"
if [[ ! -f "$SECRET_FILE" ]]; then
  if [[ -f "$JDDBMS_DATA_DIR/jddbms.mv.db" && -z "${JDDBMS_DATASOURCE_ENCRYPTION_KEY:-}" ]]; then
    echo '已有数据库但缺少原密钥文件，请恢复 data/.secrets/；不能为旧数据重新生成密钥。' >&2
    exit 1
  fi
  mkdir -p "$ROOT/data/.secrets"
  chmod 700 "$ROOT/data/.secrets"
  {
    printf 'JDDBMS_TOKEN_SECRET=%q\n' "${JDDBMS_TOKEN_SECRET:-$(head -c 48 /dev/urandom | base64 | tr -d '\n')}"
    printf 'JDDBMS_DATASOURCE_ENCRYPTION_KEY=%q\n' "${JDDBMS_DATASOURCE_ENCRYPTION_KEY:-$(head -c 32 /dev/urandom | base64 | tr -d '\n')}"
    printf 'JDDBMS_DATASOURCE_ENCRYPTION_LEGACY_KEYS=%q\n' "${JDDBMS_DATASOURCE_ENCRYPTION_LEGACY_KEYS:-}"
  } >"$SECRET_FILE"
fi
chmod 600 "$SECRET_FILE"
set -a
source "$SECRET_FILE"
set +a
for variable_name in JDDBMS_TOKEN_SECRET JDDBMS_DATASOURCE_ENCRYPTION_KEY; do
  [[ -n "${!variable_name:-}" ]] || { echo '密钥文件不完整，请从备份恢复 data/.secrets/。' >&2; exit 1; }
done

port_open() { (echo >/dev/tcp/127.0.0.1/"$1") >/dev/null 2>&1; }
wait_port() {
  local port="$1" attempts="$2"
  for ((i=0; i<attempts; i++)); do port_open "$port" && return 0; sleep 1; done
  return 1
}
wait_http() {
  local port="$1" request_path="$2" attempts="$3"
  for ((i=0; i<attempts; i++)); do http_check "$port" "$request_path" 2>/dev/null && return 0; sleep 1; done
  return 1
}
source "$ROOT/http-check.sh"

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
[[ "$nginx_template" == *'__JDDBMS_BACKEND_PORT__'* && "$nginx_template" == *'__JDDBMS_FRONTEND_PORT__'* ]] \
  || { echo 'Nginx 配置模板缺少端口占位符。' >&2; exit 1; }
nginx_template="${nginx_template//__JDDBMS_BACKEND_PORT__/$BACKEND_PORT}"
printf '%s\n' "${nginx_template//__JDDBMS_FRONTEND_PORT__/$FRONTEND_PORT}" >nginx/nginx.conf

if ! port_open "$BACKEND_PORT"; then
  nohup java -Dfile.encoding=UTF-8 "-Xms${JDDBMS_JAVA_XMS:-256m}" "-Xmx${JDDBMS_JAVA_XMX:-1024m}" -jar "$JAR_FILE" \
    --server.address=127.0.0.1 --server.port="$BACKEND_PORT" --dbms.profile="$ROOT/data" \
    --spring.profiles.active="$APPLICATION_PROFILES" \
    --spring.redis.port="$REDIS_PORT" \
    --pigenhole.uploadUrl="$ROOT/data/uploadUrl" \
    >"$ROOT/logs/app.out.log" 2>"$ROOT/logs/app.err.log" </dev/null &
  echo "$!" >run/app.pid
  wait_http "$BACKEND_PORT" /captchaImage 300 || { echo '后端启动失败，请查看 logs/app.*.log。' >&2; exit 1; }
else
  wait_http "$BACKEND_PORT" /captchaImage 3 || { echo "$BACKEND_PORT 端口已被其他服务占用。" >&2; exit 1; }
  echo '本包后端已在运行。'
fi

if ! port_open "$FRONTEND_PORT"; then
  "$NGINX_BIN" -t -p "$ROOT/" -c nginx/nginx.conf
  "$NGINX_BIN" -p "$ROOT/" -c nginx/nginx.conf
  wait_http "$FRONTEND_PORT" /healthz 20 || { echo 'Nginx 启动失败，请查看 logs/nginx.error.log。' >&2; exit 1; }
else
  wait_http "$FRONTEND_PORT" /healthz 3 || { echo "$FRONTEND_PORT 端口已被其他服务占用。" >&2; exit 1; }
  echo '本包前端已在运行。'
fi

wait_http "$FRONTEND_PORT" /dev-api/captchaImage 10 || { echo '前后端代理校验失败。' >&2; exit 1; }
echo "数据库管理平台已启动：http://<本机IP>:$FRONTEND_PORT/"
