# Fusion Operations — order fulfillment

## Design decisions

### Database indexes

PostgreSQL does not index foreign keys, and Hibernate emits no index beyond primary keys and unique constraints. The H2 database used by the tests and the local environment does index foreign keys automatically, so the gap only shows on the production engine. Six indexes are declared with `@Index` on the entities:

- **`orders (item_id, status, created_at, id)`** serves the FIFO allocation query `item_id = ? and status in (PENDING, PARTIALLY_FULFILLED) order by created_at, id`. It runs inside the allocation transaction while the item lock is held, on every incoming delivery and once per returned allocation when an order is cancelled. Status comes before created_at, because the alternative avoids the sort but has to walk every completed order of that item, and completed orders accumulate without bound while open ones do not. Index column order is a trade-off with a rationale, not a formula.
- **`inventory_movements (order_id, created_at, id)`** serves `order_id = ? order by created_at, id`. Cancellation reads it inside its transaction while the item lock is held, to find the allocations it must return. Order progress reads it too.
- **`inventory_movements (source_movement_id, id)`** is required by the foreign-key check PostgreSQL performs when a movement is deleted, which no repository method reveals. Deleting an incoming movement runs that check while the item lock is held, and without this index it is a sequential scan of the whole movements table. The same index serves `existsBySourceMovementId` and the "where this stock went" lookup.
- **`inventory_movements (item_id, created_at, id)`** serves the item history `item_id = ? order by created_at, id`, `existsByItemId`, and the `item_id` foreign key. It is read outside the item lock.
- **`orders (user_id)`** backs the `user_id` foreign key: deleting a user makes PostgreSQL check `orders` for references, and `existsByUserId` asks the same question. Every foreign key checked on delete gets an index unless a composite already covers it as its leading column (as `orders.item_id` is covered by the FIFO index). It does not run under the item lock.
- **`order_email_notifications (status, next_attempt_at)`** serves the retry scheduler's top-50 query `status = 'PENDING' and next_attempt_at <= now() order by next_attempt_at`, which runs on a fixed interval against a table that only grows. It serves both the predicate and the ordering. It does not run under the item lock.

`order_email_notifications.order_id` and the `request_id` columns are not indexed again: their unique constraints already provide an index. A seventh index, for per-item outstanding demand, is described in its own section below.

Evidence: `EXPLAIN (COSTS OFF)` on PostgreSQL 16 with 100,000 orders (90% completed), 145,000 movements and 90,000 notifications, run before and after the indexes on identical data. Plans only; timings are meaningless at this size.

| Query | Plan before | Plan after |
|---|---|---|
| FIFO allocation: orders by item and open status *(under item lock)* | Seq Scan on orders + Sort | Bitmap Index Scan on `idx_orders_item_status_created` + Sort of that item's open orders only |
| Movements of an order *(under item lock, on cancel)* | Seq Scan on inventory_movements + Sort | Index Scan on `idx_movements_order_created`, no Sort |
| FK check on movement delete *(under item lock)* | Seq Scan on inventory_movements | Index Scan on `idx_movements_source` |
| Allocations fed by a delivery | Seq Scan + Sort | Index Scan on `idx_movements_source`, no Sort |
| Item history: movements by item | Seq Scan + Sort | Bitmap Index Scan on `idx_movements_item_created` + Sort of that item's movements only |
| FK check on user delete / `existsByUserId` | Seq Scan on orders | Bitmap Index Scan on `idx_orders_user` |
| Retry scheduler, due notifications (top 50) | Seq Scan + Sort | Index Scan on `idx_notifications_status_next_attempt`, no Sort |

### List pages: client-side pagination and search

The Orders, Inventory and Catalog pages paginate (10 rows per page by default; 25, 50 or 100 on demand) and filter in the browser. Inventory searches item, SKU, reason, `#movement` and `#order`; Users search name and email; Items search name and SKU. The API keeps returning full result sets, so no endpoint contract changed late in the build. With 2,000 orders and 2,400 movements those lists respond in 10–22 ms; what needed fixing was rendering thousands of rows at once, and client-side paging solves exactly that. Server-side pagination is the step to take when result sets stop fitting comfortably in one response.

### Per-item outstanding demand

The dashboard's *Current inventory* card shows, per item, the units still owed to open orders (`GET /api/items` adds `outstandingDemand`; the other item endpoints are unchanged). It is one aggregate query, `status in (PENDING, PARTIALLY_FULFILLED) group by item_id` summing `remaining_quantity`, so the endpoint issues two statements whatever the number of items.

Per-item outstanding demand was expected to be served by the existing orders(item_id, status, created_at, id) index used for FIFO allocation. It is not: that index leads with item_id, and the aggregate for the whole list has no predicate on item_id, so PostgreSQL 16 cannot skip the leading column and falls back to a sequential scan (cost 2,622 at 100k orders, 10% open). A separate index on orders(status, item_id, remaining_quantity) turns it into an Index Only Scan over open orders only (cost 269). The assumption was checked by measurement rather than inferred from the schema.

| Variant | Plan chosen by PostgreSQL | Estimated cost |
|---|---|---|
| A · whole list, existing indexes | Seq Scan on orders + HashAggregate | 2,622 |
| B · same, sequential scans disabled | full read of the FIFO index (status is not its leading column) — rejected by the planner | 7,058 |
| D · passing all 500 item ids | Seq Scan again | 3,104 |
| D' · passing 20 item ids | Bitmap Index Scan on the FIFO index | 1,051 |
| E · whole list, new `orders(status, item_id, remaining_quantity)` | Index Only Scan over open orders only | 269 |

D' is why column order matters: the FIFO index serves this aggregate as soon as the leading column `item_id` is constrained, and not before. For the SQL Hibernate actually generates, the new index reads 16 buffer pages instead of 1,316, with 0 heap fetches. The index-only scan relies on the visibility map that autovacuum maintains; right after a bulk load, before autovacuum runs, the planner chose a bitmap heap scan (cost 1,621), which is still cheaper than the sequential scan.

This is a trade, not a free win. `status` and `remaining_quantity` change on every allocation, so every allocation now also writes an entry in this index, inside the allocation transaction while the item lock is held. Measured over 20,000 allocation-shaped writes (stock update, OUT movement insert, order update), three runs each:

| | Time per 20,000 allocations (median of 3) | WAL written | HOT updates on orders |
|---|---|---|---|
| Without the index | 2,258 ms | 26.72 MB | 0 of 20,000 |
| With the index | 2,245 ms | 28.81 MB | 0 of 20,000 |

The cost is +104 bytes of WAL per allocation (+7.8%), and the index occupies 1.06 MB per 100,000 orders. Elapsed time showed no difference beyond run-to-run noise (±10%). HOT updates were already impossible for allocations that change `status`, because the FIFO index contains it. The new index can additionally block HOT only for allocations that leave the status unchanged, and only when the page has free space; this workload did not exercise that case, so its effect is stated rather than measured. On those numbers the extra write under the lock is not material.

## Out of scope

- No migration tool. The schema is generated by Hibernate from a fresh database; `docker compose up` from a clean clone produces the full schema. A production system would use Flyway or Liquibase — adding it here would have meant a new dependency for a schema that is created once.
- List endpoints return full result sets. With the data volumes this exercise produces that is not a bottleneck, and adding pagination would have changed the shape of every list response and its frontend consumer late in the build. The indexes above are what keep the queries behind those endpoints from degrading.
