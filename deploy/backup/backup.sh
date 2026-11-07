#!/bin/sh
set -eu

: "${PGHOST:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${PGDATABASE:?}"
dir=/backups
retention="${BACKUP_RETENTION_DAYS:-14}"
stamp=$(date +%Y%m%dT%H%M%S%z)
base="${PGDATABASE}-${stamp}.dump"
name="$base"

log() { echo "[backup] $(date '+%Y-%m-%d %H:%M:%S %Z') $*"; }
fail() { log "FAILED: $*"; [ -n "${HEALTHCHECK_URL:-}" ] && wget -q -O /dev/null "${HEALTHCHECK_URL}/fail" || true; exit 1; }
trap 'rm -f "$dir/$base.partial" "$dir/$base.age.partial"' EXIT

log "dumping $PGDATABASE@$PGHOST"
pg_dump --format=custom --compress=9 --no-owner --file="$dir/$base.partial" || fail "pg_dump"
pg_restore --list "$dir/$base.partial" > /dev/null || fail "dump is not restorable"

if [ -n "${AGE_RECIPIENT:-}" ]; then
  age --encrypt --recipient "$AGE_RECIPIENT" --output "$dir/$base.age.partial" "$dir/$base.partial" || fail "age"
  name="$base.age"
  mv "$dir/$base.age.partial" "$dir/$name"
else
  mv "$dir/$base.partial" "$dir/$name"
fi
(cd "$dir" && sha256sum "$name" > "$name.sha256")
log "wrote $name ($(du -h "$dir/$name" | cut -f1))"

find "$dir" -maxdepth 1 -name "${PGDATABASE}-*.dump*" -mtime +"$retention" -print -delete | sed 's/^/[backup] pruned /'

if [ -n "${RCLONE_REMOTE:-}" ]; then
  rclone copy --no-traverse "$dir/$name" "$RCLONE_REMOTE/daily/" || fail "upload"
  rclone copy --no-traverse "$dir/$name.sha256" "$RCLONE_REMOTE/daily/" || fail "upload checksum"
  if [ "$(date +%u)" = "7" ]; then
    rclone copy --no-traverse "$dir/$name" "$RCLONE_REMOTE/weekly/" || fail "weekly upload"
  fi
  rclone delete --min-age "${REMOTE_DAILY_RETENTION:-35d}" "$RCLONE_REMOTE/daily/" || fail "remote prune"
  rclone delete --min-age "${REMOTE_WEEKLY_RETENTION:-365d}" "$RCLONE_REMOTE/weekly/" || fail "remote prune"
  log "uploaded to $RCLONE_REMOTE"
fi

date +%s > "$dir/.last-success"
[ -n "${HEALTHCHECK_URL:-}" ] && wget -q -O /dev/null "$HEALTHCHECK_URL" || true
log "done"
