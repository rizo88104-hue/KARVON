#!/bin/sh
# Disk space alert for the server (cron, see docs/server-setup.md): sends a Telegram message when a filesystem
# is fuller than DISK_ALERT_PERCENT (default 85). Reads ALERT_TELEGRAM_BOT_TOKEN and ALERT_TELEGRAM_CHAT_ID
# from the .env.prod next to docker-compose.prod.yml.
#   */15 * * * * /opt/emarket/deploy/scripts/disk-alert.sh
# Exit codes: 0 = all below the limit, 1 = alert sent/printed, 2 = could not check.
set -eu
dir="$(cd "$(dirname "$0")/../.." && pwd)"
env_file="${ENV_FILE:-$dir/.env.prod}"
get() { grep -E "^$1=" "$env_file" 2>/dev/null | tail -n 1 | cut -d= -f2- || true; }
token="$(get ALERT_TELEGRAM_BOT_TOKEN)"
chat="$(get ALERT_TELEGRAM_CHAT_ID)"
limit="${DISK_ALERT_PERCENT:-$(get DISK_ALERT_PERCENT)}"
limit="${limit:-85}"
host="$(hostname)"

# df -PT works with GNU df (Ubuntu) and BusyBox. Pseudo filesystems are skipped.
# A df failure is reported as an error, never as "all fine".
usage="$(df -PT)" || { echo "disk-alert: df failed" >&2; exit 2; }
alerts="$(printf '%s\n' "$usage" | awk -v limit="$limit" '
    NR > 1 && $2 !~ /^(tmpfs|devtmpfs|overlay|squashfs|proc|sysfs|cgroup|cgroup2)$/ {
        used = $6
        sub("%", "", used)
        if (used + 0 >= limit) printf "%s %s%% used (%s KB free)\n", $7, used, $5
    }')"

[ -z "$alerts" ] && exit 0
message="E-Market server ${host}: disk almost full
${alerts}
Check: docker system df; old images: docker image prune -a"
echo "$message"
if [ -n "$token" ] && [ -n "$chat" ]; then
    curl -fsS -m 10 "https://api.telegram.org/bot${token}/sendMessage" \
        --data-urlencode "chat_id=${chat}" --data-urlencode "text=${message}" >/dev/null
fi
exit 1
