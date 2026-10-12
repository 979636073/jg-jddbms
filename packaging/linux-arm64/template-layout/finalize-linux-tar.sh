#!/bin/sh
set -eu
name="${1:?missing package name}"
case "$name" in
  *[!A-Za-z0-9._-]*|''|.|..)
    echo 'Invalid package name' >&2
    exit 2
    ;;
esac
test -d "/bundle/$name"
cp -a "/bundle/$name" /tmp/
root="/tmp/$name"
find "$root" -type d -exec chmod 755 {} +
find "$root" -type f -exec chmod 644 {} +
find "$root" -type f -name '*.sh' -exec chmod 755 {} +
for binary in redis/bin/redis-server redis/bin/redis-cli redis/bin/redis-benchmark ssh/bin/ssh ssh/bin/sftp; do
  if [ -f "$root/$binary" ]; then chmod 755 "$root/$binary"; fi
done
for private_file in application.yml application-druid.yml authorize.properties redis/redis.conf; do
  if [ -f "$root/$private_file" ]; then chmod 600 "$root/$private_file"; fi
done
tar -czf "/bundle/$name.tar.gz" -C /tmp "$name"
test -s "/bundle/$name.tar.gz"
