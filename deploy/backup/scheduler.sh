#!/bin/sh
set -eu

at="${BACKUP_AT:-04:00}"
case "$at" in
  [0-2][0-9]:[0-5][0-9]) ;;
  *) echo "BACKUP_AT must be HH:MM, got '$at'" >&2; exit 1 ;;
esac

# Back up right away when there is no previous success, so the healthcheck reflects reality.
if [ ! -f /backups/.last-success ] || [ "${BACKUP_ON_START:-false}" = "true" ]; then
  backup.sh || echo "initial backup failed" >&2
fi

while true; do
  now=$(date +%s)
  target=$(date -d "$(date +%Y-%m-%d) $at" +%s)
  [ "$target" -le "$now" ] && target=$((target + 86400))
  echo "next backup at $(date -d "@$target" '+%Y-%m-%d %H:%M %Z')"
  sleep $((target - now))
  backup.sh || echo "backup failed at $(date '+%Y-%m-%d %H:%M:%S %Z')" >&2
done
