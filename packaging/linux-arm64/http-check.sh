#!/usr/bin/env bash
# 本地 HTTP 健康检查使用 Bash 内置功能，不依赖 curl。
http_check() (
  local port="$1" request_path="$2" status
  exec 3<>"/dev/tcp/127.0.0.1/$port" || exit 1
  printf 'GET %s HTTP/1.0\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n' "$request_path" >&3
  IFS= read -r -t 3 status <&3 || exit 1
  [[ "$status" =~ ^HTTP/1\.[01]\ 2[0-9]{2}\  ]]
)
