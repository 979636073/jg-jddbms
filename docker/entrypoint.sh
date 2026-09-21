#!/bin/sh
set -eu

secret_dir=/data/jddbms/.secrets
mkdir -p "$secret_dir"
chmod 0700 "$secret_dir"

load_or_create_secret() {
    secret_file="$1"
    if [ ! -s "$secret_file" ]; then
        umask 077
        head -c 48 /dev/urandom | base64 > "$secret_file"
    fi
    cat "$secret_file"
}

if [ -z "${JDDBMS_TOKEN_SECRET:-}" ]; then
    JDDBMS_TOKEN_SECRET="$(load_or_create_secret "$secret_dir/token-secret")"
    export JDDBMS_TOKEN_SECRET
fi

if [ -z "${JDDBMS_DATASOURCE_ENCRYPTION_KEY:-}" ]; then
    JDDBMS_DATASOURCE_ENCRYPTION_KEY="$(load_or_create_secret "$secret_dir/datasource-encryption-key")"
    export JDDBMS_DATASOURCE_ENCRYPTION_KEY
fi

exec java -jar /app/jddbms.jar
