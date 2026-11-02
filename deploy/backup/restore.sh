#!/bin/sh
# Restores a backup made by backup.sh.
#   restore.sh latest|<file name> [--from-minio] [--target-db <name>]
# Default target: the live database DB_NAME (stop api first, see docs/deploy.md). --target-db restores into another
# database (created if missing) to check a backup without touching production.
set -eu
what="${1:?usage: restore.sh latest|<file> [--from-minio] [--target-db name]}"; shift
from_minio=false
target="${DB_NAME}"
while [ $# -gt 0 ]; do
    case "$1" in
        --from-minio) from_minio=true ;;
        --target-db) target="${2:?--target-db needs a name}"; shift ;;
        *) echo "unknown option $1" >&2; exit 2 ;;
    esac
    shift
done

if [ "$from_minio" = true ]; then
    if [ "$what" = latest ]; then
        what=$(rclone lsf --include 'karvon-*.dump' "minio:${BACKUP_BUCKET:-backups}" | sort | tail -n 1)
    fi
    mkdir -p /tmp/restore
    rclone copyto "minio:${BACKUP_BUCKET:-backups}/${what}" "/tmp/restore/${what}"
    file="/tmp/restore/${what}"
else
    if [ "$what" = latest ]; then
        what=$(ls -1 /backups/karvon-*.dump | sort | tail -n 1 | xargs basename)
    fi
    file="/backups/${what}"
fi
[ -s "$file" ] || { echo "restore: $file not found" >&2; exit 1; }

psql_admin() { psql -h "${DB_HOST:-postgres}" -p "${DB_PORT:-5432}" -U "${DB_USERNAME}" -d postgres -v ON_ERROR_STOP=1 "$@"; }
if [ "$target" != "${DB_NAME}" ]; then
    psql_admin -tAc "SELECT 1 FROM pg_database WHERE datname = '${target}'" | grep -q 1 \
        || psql_admin -c "CREATE DATABASE \"${target}\""
fi

echo "restore: ${what} -> database ${target}"
pg_restore -h "${DB_HOST:-postgres}" -p "${DB_PORT:-5432}" -U "${DB_USERNAME}" -d "${target}" \
    --clean --if-exists --no-owner --exit-on-error "$file"
echo "restore: done. Flyway versions in ${target}:"
psql -h "${DB_HOST:-postgres}" -p "${DB_PORT:-5432}" -U "${DB_USERNAME}" -d "${target}" -tAc \
    "SELECT max(version::int) AS latest_migration, count(*) AS migrations FROM flyway_schema_history WHERE success"
