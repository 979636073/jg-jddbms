#!/usr/bin/env bash
# Sourced after config/local.env; credentials remain in the environment, not process arguments.
JDDBMS_META_DB_TYPE="${JDDBMS_META_DB_TYPE:-h2}"
case "$JDDBMS_META_DB_TYPE" in
  h2)
    APPLICATION_PROFILES=druid,h2
    ;;
  dm)
    [[ "${JDDBMS_META_DB_URL:-}" == jdbc:dm://* ]] \
      || { echo '达梦元数据库请配置 JDDBMS_META_DB_URL（jdbc:dm://地址:端口/模式）。' >&2; return 1; }
    [[ -n "${JDDBMS_META_DB_USERNAME:-}" && -n "${JDDBMS_META_DB_PASSWORD:-}" ]] \
      || { echo '达梦元数据库请配置 JDDBMS_META_DB_USERNAME 和 JDDBMS_META_DB_PASSWORD。' >&2; return 1; }
    export JDDBMS_META_DB_URL JDDBMS_META_DB_USERNAME JDDBMS_META_DB_PASSWORD
    APPLICATION_PROFILES=druid
    ;;
  *)
    echo 'JDDBMS_META_DB_TYPE 仅支持 h2 或 dm。' >&2
    return 1
    ;;
esac
