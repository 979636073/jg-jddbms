#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

(
  unset JDDBMS_META_DB_TYPE JDDBMS_META_DB_URL JDDBMS_META_DB_USERNAME JDDBMS_META_DB_PASSWORD
  source "$ROOT/metadata-db.sh"
  [[ "$APPLICATION_PROFILES" == druid,h2 ]]
)
(
  export JDDBMS_META_DB_TYPE=dm JDDBMS_META_DB_URL='jdbc:dm://127.0.0.1:5236/JDDBMS'
  export JDDBMS_META_DB_USERNAME=JDDBMS JDDBMS_META_DB_PASSWORD='test password $value'
  source "$ROOT/metadata-db.sh"
  [[ "$APPLICATION_PROFILES" == druid ]]
  [[ "$JDDBMS_META_DB_PASSWORD" == 'test password $value' ]]
)
for invalid_case in type url username password; do
  if (
    export JDDBMS_META_DB_TYPE=dm JDDBMS_META_DB_URL='jdbc:dm://127.0.0.1:5236/JDDBMS'
    export JDDBMS_META_DB_USERNAME=JDDBMS JDDBMS_META_DB_PASSWORD='test-only'
    case "$invalid_case" in
      type) JDDBMS_META_DB_TYPE=oracle ;;
      url) JDDBMS_META_DB_URL='jdbc:h2:file:./wrong' ;;
      username) JDDBMS_META_DB_USERNAME='' ;;
      password) JDDBMS_META_DB_PASSWORD='' ;;
    esac
    source "$ROOT/metadata-db.sh"
  ) >/dev/null 2>&1; then
    echo "无效元数据库配置未被拒绝：$invalid_case" >&2
    exit 1
  fi
done
echo '元数据库配置回归通过：默认 H2、外部 DM、特殊字符密码以及无效配置。'
