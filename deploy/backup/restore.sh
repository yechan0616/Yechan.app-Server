#!/bin/sh
# Usage:
#   restore.sh verify <file|remote>   restore into a temporary database, then drop it
#   restore.sh apply  <file|remote>   overwrite $PGDATABASE (requires RESTORE_CONFIRM=$PGDATABASE)
# Encrypted backups (.age) require AGE_IDENTITY_FILE.
set -eu

mode="${1:-}"; source="${2:-}"
[ -n "$mode" ] && [ -n "$source" ] || { sed -n '2,5p' "$0"; exit 1; }
: "${PGHOST:?}" "${PGUSER:?}" "${PGPASSWORD:?}" "${PGDATABASE:?}"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

case "$source" in
  *:*) rclone copyto "$source" "$work/$(basename "$source")"; file="$work/$(basename "$source")" ;;
  *) file="$source" ;;
esac
[ -f "$file" ] || { echo "not found: $source" >&2; exit 1; }

if [ -f "$file.sha256" ]; then (cd "$(dirname "$file")" && sha256sum -c "$(basename "$file").sha256"); fi

case "$file" in
  *.age)
    : "${AGE_IDENTITY_FILE:?AGE_IDENTITY_FILE is required for encrypted backups}"
    age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$work/backup.dump" "$file"
    file="$work/backup.dump" ;;
esac

case "$mode" in
  verify)
    db="restore_check_$(date +%s)"
    createdb "$db"
    trap 'dropdb --if-exists "$db"; rm -rf "$work"' EXIT
    pg_restore --no-owner --exit-on-error --dbname="$db" "$file"
    psql --dbname="$db" --no-psqlrc --command \
      "select 'blueprint' as table, count(*) from blueprint union all select 'entry', count(*) from entry union all select 'administrator', count(*) from administrator"
    echo "restore verified: $source"
    ;;
  apply)
    [ "${RESTORE_CONFIRM:-}" = "$PGDATABASE" ] || { echo "set RESTORE_CONFIRM=$PGDATABASE to overwrite it" >&2; exit 1; }
    pg_restore --no-owner --clean --if-exists --exit-on-error --single-transaction --dbname="$PGDATABASE" "$file"
    echo "restored into $PGDATABASE"
    ;;
  *) echo "unknown mode: $mode" >&2; exit 1 ;;
esac
