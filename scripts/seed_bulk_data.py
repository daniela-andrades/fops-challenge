#!/usr/bin/env python3
"""
Loads a large, realistic volume of data through the public API, to see how the list pages and endpoints behave
with many rows. Everything goes through the API, so allocation, movements, emails and cancellations follow the
same business rules as manual use. Deterministic (fixed random seed).

Usage: python3 scripts/seed_bulk_data.py [--orders 2000] [--deliveries 1000] [--cancellations 60]
       (API_URL defaults to http://localhost:8080/api)

Demand is uneven on purpose: ten "hot" items receive half of the orders and build up a backlog of pending and
partial orders, while the rest accumulate stock. Go back to the normal demo data with:
    scripts/dev.sh reset && scripts/dev.sh up && scripts/dev.sh seed
"""
import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request

API = os.environ.get("API_URL", "http://localhost:8080/api")


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(API + path, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"null")


def timed_get(path):
    start = time.perf_counter()
    with urllib.request.urlopen(API + path, timeout=60) as response:
        raw = response.read()
    return (time.perf_counter() - start) * 1000, len(json.loads(raw)), len(raw)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--users", type=int, default=20)
    parser.add_argument("--items", type=int, default=50)
    parser.add_argument("--orders", type=int, default=2000)
    parser.add_argument("--deliveries", type=int, default=1000)
    parser.add_argument("--cancellations", type=int, default=60)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    rng = random.Random(args.seed)

    try:
        status, items = call("GET", "/items")
    except urllib.error.URLError:
        sys.exit(f"Backend is not reachable at {API}. Start it with: scripts/dev.sh up")
    if any(item["sku"].startswith("BULK-") for item in items):
        sys.exit("Bulk data is already loaded. Reset first: scripts/dev.sh reset && scripts/dev.sh up")

    started = time.perf_counter()
    users = [call("POST", "/users", {"name": f"Bulk User {n:02d}", "email": f"bulk{n:02d}@load.test"})[1]["id"]
             for n in range(1, args.users + 1)]
    items = [call("POST", "/items", {"name": f"Bulk Item {n:03d}", "sku": f"BULK-{n:03d}",
                                     "stockOnHand": rng.choice([0, 0, 10, 25, 50])})[1]["id"]
             for n in range(1, args.items + 1)]
    hot = items[:10]
    print(f"  ✔ {len(users)} users and {len(items)} items (10 of them with high demand)")

    events = ["order"] * args.orders + ["delivery"] * args.deliveries
    rng.shuffle(events)
    created_orders, failures = [], 0
    for n, event in enumerate(events, start=1):
        if event == "order":
            item = rng.choice(hot) if rng.random() < 0.5 else rng.choice(items)
            status, body = call("POST", "/orders", {"userId": rng.choice(users), "itemId": item,
                                                     "requestedQuantity": rng.randint(1, 12)})
            if status == 201:
                created_orders.append(body["id"])
        else:
            status, _ = call("POST", "/inventory/incoming", {"itemId": rng.choice(items), "quantity": rng.randint(5, 20),
                                                               "reason": f"Bulk delivery {n}"})
        failures += status >= 300
        if n % 500 == 0:
            print(f"  … {n}/{len(events)} requests")
    print(f"  ✔ {args.orders} orders and {args.deliveries} deliveries ({failures} failed requests)")

    _, open_orders = call("GET", "/orders")
    candidates = [o["id"] for o in open_orders
                  if o["id"] in set(created_orders) and o["status"] in ("PENDING", "PARTIALLY_FULFILLED")]
    cancelled = 0
    for order_id in rng.sample(candidates, min(args.cancellations, len(candidates))):
        cancelled += call("POST", f"/orders/{order_id}/cancel")[0] == 200
    print(f"  ✔ {cancelled} open orders cancelled (their stock returned and re-allocated)")
    print(f"  Loaded in {time.perf_counter() - started:.0f} s")

    _, summary = call("GET", "/dashboard/summary")
    print("\nDashboard:", ", ".join(f"{k}={v}" for k, v in summary.items()))
    print("\nList endpoints (full result sets, no pagination):")
    for path in ("/orders", "/inventory/movements", "/users", "/items"):
        ms, rows, size = timed_get(path)
        print(f"  GET {path:<22} {rows:>6} rows  {size / 1024:>7.0f} KB  {ms:>6.0f} ms")


if __name__ == "__main__":
    main()
