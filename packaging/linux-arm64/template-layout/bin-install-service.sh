#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT=/etc/systemd/system/jddbms.service
[[ "${EUID}" == 0 ]] || { echo '请以 root 权限运行。' >&2; exit 1; }
[[ "$ROOT" != *[[:space:]]* ]] || { echo '安装目录不能包含空格。' >&2; exit 1; }
case "${1:-}" in
  install)
    cat >"$UNIT" <<EOF
[Unit]
Description=JDDBMS Database Management Platform
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$ROOT
ExecStart=/usr/bin/bash $ROOT/start.sh
ExecStop=/usr/bin/bash $ROOT/stop.sh
TimeoutStartSec=180
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOF
    chmod 644 "$UNIT"
    systemctl daemon-reload
    systemctl enable --now jddbms.service
    ;;
  uninstall)
    systemctl disable --now jddbms.service || true
    rm -f "$UNIT"
    systemctl daemon-reload
    ;;
  *)
    echo '用法：bash bin/install-service.sh install|uninstall' >&2
    exit 2
    ;;
esac
