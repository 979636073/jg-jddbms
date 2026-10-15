#!/usr/bin/env bash
# 仅用于没有业务数据的隔离 ARM64 测试目录，不用于升级现场配置。
set -euo pipefail
ROOT="${1:?请指定隔离测试包目录}"
cd "$ROOT"
[[ ! -f config/local.env && ! -f data/h2/jddbms.mv.db ]] || { echo '测试目录已有配置或数据库，拒绝覆盖。' >&2; exit 1; }
command -v nginx >/dev/null 2>&1 && { echo '测试环境不应预装 Nginx。' >&2; exit 1; }
command -v redis-server >/dev/null 2>&1 && { echo '测试环境不应预装 Redis。' >&2; exit 1; }
command -v curl >/dev/null 2>&1 && { echo '测试环境不应预装 curl，以验证无 curl 启动。' >&2; exit 1; }
umask 077
chmod 700 redis/redis-server redis/redis-cli nginx/nginx
for script in start.sh stop.sh status.sh http-check.sh metadata-db.sh; do bash -n "$script"; done
source "$ROOT/http-check.sh"

start_emulated_redis() {
  local port="$1"
  # 仅 QEMU 测试允许绕过模拟内核的 COW 检查，生产 start.sh 不使用此选项。
  if [[ "${JDDBMS_QA_EMULATED_ARM:-0}" == 1 ]]; then
    mkdir -p run logs data/redis
    "$ROOT/redis/redis-server" --bind 127.0.0.1 --port "$port" --daemonize yes \
      --dir "$ROOT/data/redis" --appendonly yes --pidfile "$ROOT/run/redis.pid" \
      --logfile "$ROOT/logs/redis.log" --ignore-warnings ARM64-COW-BUG
  fi
}
start_emulated_redis 6379
bash start.sh
bash status.sh
http_check 18081 /
http_check 18081 /dev-api/captchaImage
grep -q '数据库管理软件' web/index.html
[[ -s config/local.env ]]
if grep -Eq '^JDDBMS_(TOKEN_SECRET|DATASOURCE_ENCRYPTION_KEY)=' config/local.env; then exit 1; fi
[[ -s data/h2/jddbms.mv.db ]]
[[ -s data/.secrets/runtime.env && "$(stat -c %a data/.secrets/runtime.env)" == 600 ]]
secret_before="$(sha256sum data/.secrets/runtime.env)"
"$ROOT/redis/redis-cli" -p 6379 set native-runtime-smoke persisted >/dev/null
bash stop.sh
for ((attempt=0; attempt<60; attempt++)); do
  if ! (echo >/dev/tcp/127.0.0.1/28080) >/dev/null 2>&1 \
      && ! (echo >/dev/tcp/127.0.0.1/6379) >/dev/null 2>&1 \
      && ! (echo >/dev/tcp/127.0.0.1/18081) >/dev/null 2>&1; then break; fi
  sleep 1
done
sed -i "s/JDDBMS_BACKEND_PORT='28080'/JDDBMS_BACKEND_PORT='28083'/;s/JDDBMS_REDIS_PORT='6379'/JDDBMS_REDIS_PORT='6383'/;s/JDDBMS_FRONTEND_PORT='18081'/JDDBMS_FRONTEND_PORT='18083'/" config/local.env
start_emulated_redis 6383
bash start.sh
http_check 18083 /
http_check 18083 /dev-api/captchaImage
[[ "$("$ROOT/redis/redis-cli" -p 6383 get native-runtime-smoke)" == persisted ]]
[[ -s data/h2/jddbms.mv.db ]]
[[ "$(sha256sum data/.secrets/runtime.env)" == "$secret_before" ]]
bash status.sh
echo 'ARM64 原生包：无系统 Nginx/Redis/curl、自定义三端口、密钥自动生成与重启沿用、H2 初始化及 Redis 持久化验证通过。'
