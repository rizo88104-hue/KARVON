#!/bin/sh
# cron (default): run backup.sh every day at BACKUP_CRON (default 03:00 Asia/Tashkent).
# backup | restore ... | init-buckets: run that script once.
set -eu
# rclone remote "minio" from the environment (no config file).
export RCLONE_CONFIG_MINIO_TYPE=s3
export RCLONE_CONFIG_MINIO_PROVIDER=Minio
export RCLONE_CONFIG_MINIO_ENDPOINT="${S3_ENDPOINT:-http://minio:9000}"
export RCLONE_CONFIG_MINIO_ACCESS_KEY_ID="${S3_ACCESS_KEY}"
export RCLONE_CONFIG_MINIO_SECRET_ACCESS_KEY="${S3_SECRET_KEY}"
export RCLONE_CONFIG_MINIO_REGION="${S3_REGION:-us-east-1}"
export PGPASSWORD="${DB_PASSWORD}"

case "${1:-cron}" in
    cron)
        echo "${BACKUP_CRON:-0 3 * * *} /usr/local/bin/backup.sh >> /proc/1/fd/1 2>&1" > /etc/crontabs/root
        echo "backup: scheduled '${BACKUP_CRON:-0 3 * * *}' (TZ=${TZ:-UTC})"
        exec crond -f -l 8
        ;;
    backup) shift; exec /usr/local/bin/backup.sh "$@" ;;
    restore) shift; exec /usr/local/bin/restore.sh "$@" ;;
    init-buckets) shift; exec /usr/local/bin/init-buckets.sh "$@" ;;
    *) exec "$@" ;;
esac
