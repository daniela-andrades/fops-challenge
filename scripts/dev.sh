#!/usr/bin/env bash
# Local environment for manual testing, without Docker:
#   backend  -> http://localhost:8080  (Spring Boot, H2 file database in .dev/db)
#   frontend -> http://localhost:4200  (ng serve, proxies /api to the backend)
#   mail     -> http://localhost:8025  (dev inbox, SMTP on port 1025)
#
# Usage: scripts/dev.sh <command>
#   up           build the backend and start everything
#   down         stop everything
#   status       show what is running
#   seed         load demo data (only into an empty database; add --force to load anyway)
#   reset        stop everything and delete the local database
#   mail-stop    stop only the mail server (simulates an SMTP outage)
#   mail-start   start the mail server again
#   logs <name>  follow a log: backend | frontend | mail
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV="$ROOT/.dev"
LOGS="$DEV/logs"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend/frontend/fops-frontend"
BACKEND_JAR="$BACKEND_DIR/target/fops-backend-0.0.1-SNAPSHOT.jar"

BACKEND_PORT=8080
FRONTEND_PORT=4200
SMTP_PORT=1025
INBOX_PORT=8025

mkdir -p "$LOGS" "$DEV/db"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
ok() { printf '  \033[32m✔\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✘\033[0m %s\n' "$*"; exit 1; }

pid_on_port() { lsof -ti "tcp:$1" -sTCP:LISTEN 2>/dev/null | head -1 || true; }

wait_for_url() {
  local url="$1" name="$2" seconds="$3"
  for _ in $(seq 1 "$seconds"); do
    if curl -s -o /dev/null "$url"; then ok "$name ready"; return 0; fi
    sleep 1
  done
  fail "$name did not start in ${seconds}s, see: scripts/dev.sh logs $(echo "$name" | tr 'A-Z' 'a-z')"
}

stop_port() {
  local port="$1" name="$2" pid
  pid="$(pid_on_port "$port")"
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 20); do [ -z "$(pid_on_port "$port")" ] && break; sleep 0.5; done
    ok "$name stopped"
  else
    ok "$name was not running"
  fi
}

java21_home() {
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
    echo "$JAVA_HOME"
  else
    /usr/libexec/java_home -v 21 2>/dev/null || fail "JDK 21 not found (install Temurin 21)"
  fi
}

start_mail() {
  if [ -n "$(pid_on_port "$SMTP_PORT")" ]; then ok "Mail server already running"; return; fi
  nohup python3 "$ROOT/scripts/dev_mail_server.py" --smtp-port "$SMTP_PORT" --http-port "$INBOX_PORT" --store "$DEV/inbox.json" \
    < /dev/null > "$LOGS/mail.log" 2>&1 &
  wait_for_url "http://localhost:$INBOX_PORT" "Mail" 15
}

start_backend() {
  if [ -n "$(pid_on_port "$BACKEND_PORT")" ]; then ok "Backend already running"; return; fi
  local java_home
  java_home="$(java21_home)"
  echo "  Building backend (skipping tests)..."
  (cd "$BACKEND_DIR" && JAVA_HOME="$java_home" mvn -q -B -DskipTests package) > "$LOGS/backend-build.log" 2>&1 \
    || fail "Backend build failed, see $LOGS/backend-build.log"

  SPRING_DATASOURCE_URL="jdbc:h2:file:$DEV/db/fops;MODE=PostgreSQL;AUTO_SERVER=TRUE" \
  NOTIFICATION_RETRY_BASE_DELAY_MS=10000 \
  NOTIFICATION_POLL_INTERVAL_MS=5000 \
  NOTIFICATION_MAX_ATTEMPTS=4 \
  MAIL_CONNECTION_TIMEOUT_MS=2000 \
  nohup "$java_home/bin/java" -jar "$BACKEND_JAR" < /dev/null > "$LOGS/backend.log" 2>&1 &
  wait_for_url "http://localhost:$BACKEND_PORT/api/dashboard/summary" "Backend" 90
}

start_frontend() {
  if [ -n "$(pid_on_port "$FRONTEND_PORT")" ]; then ok "Frontend already running"; return; fi
  if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    echo "  Installing frontend dependencies..."
    (cd "$FRONTEND_DIR" && npm ci) > "$LOGS/frontend-install.log" 2>&1 || fail "npm ci failed, see $LOGS/frontend-install.log"
  fi
  # exec inside the detached shell so no wrapper process keeps the caller's terminal open
  nohup bash -c "cd '$FRONTEND_DIR' && exec npx ng serve --port $FRONTEND_PORT" < /dev/null > "$LOGS/frontend.log" 2>&1 &
  wait_for_url "http://localhost:$FRONTEND_PORT" "Frontend" 120
}

status() {
  local name port url
  for entry in "Frontend:$FRONTEND_PORT:http://localhost:$FRONTEND_PORT" \
               "Backend:$BACKEND_PORT:http://localhost:$BACKEND_PORT/api" \
               "Mail inbox:$SMTP_PORT:http://localhost:$INBOX_PORT" \
               "H2 console:$BACKEND_PORT:http://localhost:$BACKEND_PORT/h2-console"; do
    name="${entry%%:*}"; port="$(echo "$entry" | cut -d: -f2)"; url="$(echo "$entry" | cut -d: -f3-)"
    if [ -n "$(pid_on_port "$port")" ]; then ok "$(printf '%-11s %s' "$name" "$url")"; else warn "$(printf '%-11s not running' "$name")"; fi
  done
}

case "${1:-}" in
  up)
    bold "Starting local environment"
    start_mail
    start_backend
    start_frontend
    echo
    status
    echo
    echo "  H2 console JDBC URL: jdbc:h2:file:$DEV/db/fops;MODE=PostgreSQL;AUTO_SERVER=TRUE  (user sa, empty password)"
    echo "  Load demo data with: scripts/dev.sh seed"
    ;;
  down)
    bold "Stopping local environment"
    stop_port "$FRONTEND_PORT" "Frontend"
    stop_port "$BACKEND_PORT" "Backend"
    stop_port "$SMTP_PORT" "Mail server"
    ;;
  status)
    status
    ;;
  seed)
    "$ROOT/scripts/seed-demo-data.sh" "${2:-}"
    ;;
  reset)
    bold "Resetting local environment"
    "$0" down
    rm -rf "$DEV/db" "$DEV/inbox.json"
    ok "Local database and inbox deleted"
    ;;
  mail-stop)
    stop_port "$SMTP_PORT" "Mail server"
    echo "  Emails will now fail and be retried (10s, 20s, 40s). Restore with: scripts/dev.sh mail-start"
    ;;
  mail-start)
    start_mail
    ;;
  logs)
    tail -n 50 -f "$LOGS/${2:-backend}.log"
    ;;
  *)
    sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
