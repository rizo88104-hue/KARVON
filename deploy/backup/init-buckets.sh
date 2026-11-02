#!/bin/sh
# Creates the photo and backup buckets (idempotent). Waits for MinIO, which has no healthcheck of its own.
set -eu
for i in $(seq 1 60); do
    if rclone lsd minio: >/dev/null 2>&1; then
        rclone mkdir "minio:${S3_BUCKET}"
        rclone mkdir "minio:${BACKUP_BUCKET:-backups}"
        echo "init-buckets: ${S3_BUCKET} and ${BACKUP_BUCKET:-backups} are ready"
        exit 0
    fi
    sleep 2
done
echo "init-buckets: MinIO did not answer" >&2
exit 1
