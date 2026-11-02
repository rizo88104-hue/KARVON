#!/bin/sh
# One backup: pg_dump (custom format, compressed) -> /backups (host directory) -> MinIO bucket
# -> optional off-site rclone remote. Keeps BACKUP_RETENTION_DAYS (default 30) in every location.
set -eu
retention="${BACKUP_RETENTION_DAYS:-30}"
name="karvon-$(date +%Y%m%d-%H%M%S).dump"
file="/backups/${name}"
mkdir -p /backups

pg_dump -h "${DB_HOST:-postgres}" -p "${DB_PORT:-5432}" -U "${DB_USERNAME}" -d "${DB_NAME}" \
    --format=custom --compress=9 --no-owner --file="${file}.part"
mv "${file}.part" "${file}"
# A dump that pg_restore cannot list is not a backup.
pg_restore --list "${file}" >/dev/null
size=$(du -h "${file}" | cut -f1)

rclone copyto "${file}" "minio:${BACKUP_BUCKET:-backups}/${name}"
if [ -n "${BACKUP_OFFSITE_REMOTE:-}" ]; then
    rclone copyto "${file}" "${BACKUP_OFFSITE_REMOTE}/${name}"
fi

# Retention: local files, MinIO objects and the off-site copies older than N days.
find /backups -name 'karvon-*.dump' -type f -mtime "+${retention}" -delete
rclone delete --min-age "${retention}d" --include 'karvon-*.dump' "minio:${BACKUP_BUCKET:-backups}"
if [ -n "${BACKUP_OFFSITE_REMOTE:-}" ]; then
    rclone delete --min-age "${retention}d" --include 'karvon-*.dump' "${BACKUP_OFFSITE_REMOTE}"
fi
echo "backup: ${name} (${size}) saved to /backups, minio:${BACKUP_BUCKET:-backups}${BACKUP_OFFSITE_REMOTE:+ and ${BACKUP_OFFSITE_REMOTE}}"
