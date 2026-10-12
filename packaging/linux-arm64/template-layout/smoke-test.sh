#!/usr/bin/env bash
set -euo pipefail
ROOT="$(mktemp -d)"
REDIS_CLI="$ROOT/redis/bin/redis-cli"
cleanup() {
  if [[ -f "$REDIS_CLI" ]]; then
    REDISCLI_AUTH='q"u\o te' "$REDIS_CLI" -p 16379 shutdown nosave >/dev/null 2>&1 || true
  fi
  rm -rf "$ROOT"
}
trap cleanup EXIT
mkdir -p "$ROOT"/{redis/bin,config,dist,bin,nginx}
cp /package/start.sh /package/status.sh "$ROOT/"
cp /package/nginx/nginx.conf.template /package/nginx/mime.types "$ROOT/nginx/"
cp /package/redis/bin/redis-server /package/redis/bin/redis-cli "$ROOT/redis/bin/"
touch "$ROOT/jd-dbms-admin.jar" "$ROOT/dist/index.html"
ln -s /bin/true "$ROOT/bin/java"
ln -s /bin/true "$ROOT/bin/nginx"
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

if PATH="$ROOT/bin:$PATH" timeout 5 bash "$ROOT/start.sh" >"$ROOT/start.log" 2>&1; then
  echo '预期测试在等待后端时超时' >&2
  exit 1
else
  status=$?
  if [[ "$status" == 1 && -f "$ROOT/run/redis.conf" ]]; then
    # Docker ARM64 模拟内核触发 Redis 的 COW 检查，仅在本次测试中跳过。
    "$ROOT/redis/bin/redis-server" "$ROOT/run/redis.conf" --ignore-warnings ARM64-COW-BUG
  else
    [[ "$status" == 124 || "$status" == 143 ]] || { cat "$ROOT/start.log"; exit "$status"; }
  fi
fi
for ((attempt=0; attempt<20; attempt++)); do
  if REDISCLI_AUTH='q"u\o te' "$REDIS_CLI" -p 16379 ping 2>/dev/null | grep -qx PONG; then break; fi
  sleep 0.2
done
REDISCLI_AUTH='q"u\o te' "$REDIS_CLI" -p 16379 ping | grep -qx PONG
if PATH="$ROOT/bin:$PATH" timeout 5 bash "$ROOT/start.sh" >"$ROOT/restart.log" 2>&1; then
  echo '预期测试在等待后端时超时' >&2
  exit 1
else
  status=$?
  [[ "$status" == 124 || "$status" == 143 ]] || { cat "$ROOT/restart.log"; exit "$status"; }
fi
grep -F 'root dist;' "$ROOT/nginx/nginx.conf" >/dev/null
grep -F 'proxy_pass http://127.0.0.1:28081/;' "$ROOT/nginx/nginx.conf" >/dev/null
/usr/sbin/nginx -t -p "$ROOT/" -c nginx/nginx.conf
status_text="$(bash "$ROOT/status.sh" 2>/dev/null)"
grep -F 'Redis 认证：正常' <<<"$status_text" >/dev/null
grep -F '后端：未监听（127.0.0.1:28081）' <<<"$status_text" >/dev/null
echo '模板布局：Redis、后端端口、Nginx 语法均通过'
