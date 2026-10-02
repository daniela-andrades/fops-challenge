#!/usr/bin/env bash
# Loads demo data through the public API, so it follows every business rule.
# Resulting state (ready for docs/manual-testing.md):
#   Laptop   (LAP-001) stock 15  <- order by Ana for 5 was completed from stock (1 email)
#   Monitor  (MON-001) stock 0   <- order by Luis for 8 is PARTIALLY_FULFILLED (5/8)
#   Keyboard (KEY-001) stock 0   <- Marta (4, partial 3/4) and then Ana (6, pending) wait in FIFO order
#   Mouse    (MOU-001) stock 50  <- no orders yet
#
# Usage: scripts/seed-demo-data.sh [--force]    (API_URL defaults to http://localhost:8080/api)
set -euo pipefail

API="${API_URL:-http://localhost:8080/api}"

post() {
  local response status
  response="$(curl -s -w '\n%{http_code}' -H 'Content-Type: application/json' -X POST "$API/$1" -d "$2")"
  status="${response##*$'\n'}"
  response="${response%$'\n'*}"
  if [ "$status" -ge 300 ]; then
    echo "  ✘ POST /$1 -> $status $response" >&2
    exit 1
  fi
  echo "$response" | python3 -c 'import sys, json; print(json.load(sys.stdin)["id"])'
}

curl -s -o /dev/null "$API/dashboard/summary" || { echo "Backend is not reachable at $API. Start it with: scripts/dev.sh up" >&2; exit 1; }

existing="$(curl -s "$API/dashboard/summary" | python3 -c 'import sys, json; d = json.load(sys.stdin); print(d["totalUsers"] + d["totalItems"])')"
if [ "$existing" -gt 0 ] && [ "${1:-}" != "--force" ]; then
  echo "The database already has data; nothing was loaded."
  echo "To start clean: 'docker compose down -v' (Docker) or 'scripts/dev.sh reset' (development mode). To load anyway, pass --force."
  exit 0
fi

echo "Loading demo data into $API"

ana="$(post users '{"name":"Ana Garcia","email":"ana@fops.local"}')"
luis="$(post users '{"name":"Luis Perez","email":"luis@fops.local"}')"
marta="$(post users '{"name":"Marta Ruiz","email":"marta@fops.local"}')"
echo "  ✔ 3 users: Ana ($ana), Luis ($luis), Marta ($marta)"

laptop="$(post items '{"name":"Laptop","sku":"LAP-001","stockOnHand":20}')"
monitor="$(post items '{"name":"Monitor","sku":"MON-001","stockOnHand":5}')"
keyboard="$(post items '{"name":"Keyboard","sku":"KEY-001","stockOnHand":0}')"
mouse="$(post items '{"name":"Mouse","sku":"MOU-001","stockOnHand":50}')"
echo "  ✔ 4 items: Laptop ($laptop), Monitor ($monitor), Keyboard ($keyboard), Mouse ($mouse)"

o1="$(post orders "{\"userId\":$ana,\"itemId\":$laptop,\"requestedQuantity\":5}")"
o2="$(post orders "{\"userId\":$luis,\"itemId\":$monitor,\"requestedQuantity\":8}")"
o3="$(post orders "{\"userId\":$marta,\"itemId\":$keyboard,\"requestedQuantity\":4}")"
sleep 1
o4="$(post orders "{\"userId\":$ana,\"itemId\":$keyboard,\"requestedQuantity\":6}")"
echo "  ✔ 4 orders: #$o1 completed, #$o2 partial, #$o3 and #$o4 waiting for keyboards"

in1="$(post inventory/incoming "{\"itemId\":$keyboard,\"quantity\":3,\"reason\":\"Supplier A - partial delivery\"}")"
echo "  ✔ Incoming movement #$in1: 3 keyboards, allocated to the oldest order (#$o3 is now 3/4)"

echo
echo "Done. Open http://localhost:4200 and follow docs/manual-testing.md"
