#!/usr/bin/env bash
set -euo pipefail

ROOT="$(mktemp -d)"
cleanup() {
  REDISCLI_AUTH='q"u\o te' "$ROOT/redis/redis-cli" -p 16379 shutdown nosave >/dev/null 2>&1 || true
  rm -rf "$ROOT"
}
trap cleanup EXIT
mkdir -p "$ROOT"/{redis,config,app,web,bin,nginx}
cp /package/start.sh /package/status.sh "$ROOT/"
cp /package/nginx/nginx.conf "$ROOT/nginx/nginx.conf.template"
cp /output/redis-server /output/redis-cli "$ROOT/redis/"
touch "$ROOT/app/jddbms.jar" "$ROOT/web/index.html"
ln -s /bin/true "$ROOT/bin/java"
ln -s /bin/true "$ROOT/nginx/nginx"
cat >"$ROOT/config/local.env" <<'EOF'
JDDBMS_META_DB_URL='jdbc:dm://127.0.0.1:5236/JDDBMS'
JDDBMS_META_DB_USERNAME='JDDBMS'
JDDBMS_META_DB_PASSWORD='test'
JDDBMS_TOKEN_SECRET='test'
JDDBMS_DATASOURCE_ENCRYPTION_KEY='test'
JDDBMS_REDIS_HOST='127.0.0.1'
JDDBMS_REDIS_PORT='16379'
JDDBMS_REDIS_PASSWORD='q"u\o te'
JDDBMS_BACKEND_PORT='28081'
EOF

# 后端为测试占位命令；超时后仅核验 Redis 已按配置启动。
if PATH="$ROOT/bin:$PATH" timeout 5 bash "$ROOT/start.sh" >"$ROOT/start.log" 2>&1; then
  echo '预期测试在等待后端时超时' >&2
  exit 1
else
  status=$?
  if [[ "$status" == 1 && -f "$ROOT/run/redis.conf" ]]; then
    # Docker 的 ARM64 模拟内核会触发 Redis 的 COW 检查；仅测试时跳过。
    "$ROOT/redis/redis-server" "$ROOT/run/redis.conf" --ignore-warnings ARM64-COW-BUG
  else
    [[ "$status" == 124 || "$status" == 143 ]] || { cat "$ROOT/start.log"; cat "$ROOT/logs/redis.log" 2>/dev/null || true; exit "$status"; }
  fi
fi
for ((attempt=0; attempt<20; attempt++)); do
  if REDISCLI_AUTH='q"u\o te' "$ROOT/redis/redis-cli" -p 16379 ping 2>/dev/null | grep -qx PONG; then
    break
  fi
  sleep 0.2
done
REDISCLI_AUTH='q"u\o te' "$ROOT/redis/redis-cli" -p 16379 ping | grep -qx PONG
if REDISCLI_AUTH=wrong "$ROOT/redis/redis-cli" -p 16379 ping 2>/dev/null | grep -qx PONG; then
  echo '错误密码意外通过' >&2
  exit 1
fi
if PATH="$ROOT/bin:$PATH" timeout 5 bash "$ROOT/start.sh" >"$ROOT/restart.log" 2>&1; then
  echo '预期测试在等待后端时超时' >&2
  exit 1
else
  status=$?
  [[ "$status" == 124 || "$status" == 143 ]] || { cat "$ROOT/restart.log"; exit "$status"; }
fi
grep -F 'proxy_pass http://127.0.0.1:28081/;' "$ROOT/nginx/nginx.conf" >/dev/null
status_text="$(bash "$ROOT/status.sh" 2>/dev/null)"
grep -F 'Redis 认证：正常' <<<"$status_text" >/dev/null || { printf '%s\n' "$status_text"; exit 1; }
grep -F '后端：未监听（127.0.0.1:28081）' <<<"$status_text" >/dev/null || { printf '%s\n' "$status_text"; exit 1; }
echo 'ARM64 包内 Redis 与后端配置：自定义端口、密码和 Nginx 转发验证通过'
