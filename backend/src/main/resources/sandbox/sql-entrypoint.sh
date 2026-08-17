#!/bin/sh
# Runs as the override Entrypoint of a throwaway `postgres:16-alpine` container (no custom image
# build needed -- this file is bind-mounted in read-only by DockerSqlExecutionService). Everything
# happens inside ONE network-disabled container: init a scratch Postgres instance in an
# in-container-only data dir (never bind-mounted, never persisted), seed it, run the student's
# query and the admin's reference query against the identical seeded data, dump both result sets
# to CSV under /sandbox/output, shut down. The container (and everything in it) is destroyed by
# the host immediately after this script exits -- nothing here ever survives one execution.
#
# Starts as root (matching the official postgres image's own convention -- its own entrypoint.sh
# does the same) only to prepare/chown the data directory; every actual Postgres/psql command runs
# as the pre-existing unprivileged `postgres` user via su-exec. Combined with --network=none,
# --cap-drop=ALL, --pids-limit and memory limits set by the caller, this is materially safer than
# it looks from "starts as root" alone.
set -u

PGDATA=/tmp/pgdata
mkdir -p "$PGDATA"
chown -R postgres:postgres "$PGDATA" 2>/dev/null || true

IN=/sandbox/input
OUT=/sandbox/output
mkdir -p "$OUT"

su-exec postgres initdb --username=sandbox --auth=trust -D "$PGDATA" > "$OUT/initdb.log" 2>&1
su-exec postgres pg_ctl -D "$PGDATA" -o "-c listen_addresses=127.0.0.1 -c fsync=off -c full_page_writes=off" -w start > "$OUT/pg_ctl.log" 2>&1
if [ $? -ne 0 ]; then
  echo "STARTUP_FAILED" > "$OUT/status"
  exit 1
fi

su-exec postgres createdb --username=sandbox sandbox >> "$OUT/pg_ctl.log" 2>&1

if [ -s "$IN/seed.sql" ]; then
  su-exec postgres psql -v ON_ERROR_STOP=1 -U sandbox -d sandbox -f "$IN/seed.sql" > "$OUT/seed.log" 2>&1
  if [ $? -ne 0 ]; then
    cp "$OUT/seed.log" "$OUT/error"
    echo "SEED_FAILED" > "$OUT/status"
    su-exec postgres pg_ctl -D "$PGDATA" -m fast -w stop > /dev/null 2>&1
    exit 1
  fi
fi

run_query() {
  # $1 = file with the raw (already single-statement-validated) query text
  # $2 = output CSV filename under $OUT
  # $3 = exit-code/log basename under $OUT
  {
    printf 'SET statement_timeout = %s;\n' "${STATEMENT_TIMEOUT_MS:-5000}"
    printf '\\copy ('
    cat "$1"
    printf ') to '"'"'%s'"'"' with (format csv, header true)\n' "$OUT/$2"
  } > /tmp/wrapper.sql
  su-exec postgres psql -v ON_ERROR_STOP=1 -U sandbox -d sandbox -f /tmp/wrapper.sql > "$OUT/$3.log" 2>&1
  echo $? > "$OUT/$3"
}

if [ -s "$IN/student.sql" ]; then
  run_query "$IN/student.sql" "student_result.csv" "student_exit"
fi
if [ -s "$IN/reference.sql" ]; then
  run_query "$IN/reference.sql" "reference_result.csv" "reference_exit"
fi

echo "DONE" > "$OUT/status"
su-exec postgres pg_ctl -D "$PGDATA" -m fast -w stop > /dev/null 2>&1
exit 0
