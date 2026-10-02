# Fusion Operations — order fulfillment

An order management system where orders are fulfilled automatically against inventory. An order takes whatever stock is on hand when it is created, and whatever it still lacks is served, oldest order first, as soon as new stock arrives. Every unit can be traced from the delivery that brought it to the order that consumed it, and the user is emailed when an order reaches 100%.

Spring Boot 3.3 (Java 21) and PostgreSQL 16 on the backend, Angular 21 on the frontend, MailHog to catch the emails.

## Quick start

### With Docker (recommended)

Prerequisites: Docker with Compose v2. Ports 4200, 8080, 8025, 1025 and 5433 must be free.

```bash
git clone https://github.com/daniela-andrades/fops-challenge.git
cd fops-challenge
docker compose up --build
```

The first build downloads the Maven and npm dependencies and takes a few minutes. Then open:

| What | URL |
|---|---|
| Web app | http://localhost:4200 |
| REST API | http://localhost:8080/api (for example `/api/orders`) |
| MailHog, the inbox for order-completed emails | http://localhost:8025 |
| PostgreSQL | `localhost:5433`, database `fops`, user `fops`, password `password` |

The database starts empty. To load a small demo data set (four items, three users, orders in every state), run this from another terminal:

```bash
scripts/seed-demo-data.sh
```

Stop with `docker compose down`, or with `docker compose down -v` to also delete the database.

### Development mode (without Docker)

Prerequisites: JDK 21, Maven 3.9, Node 22 with npm, and Python 3 (for the local mail inbox).

```bash
scripts/dev.sh up       # builds the backend, starts backend, frontend and a local mail inbox
scripts/dev.sh seed     # loads the demo data
scripts/dev.sh status   # shows what is running
scripts/dev.sh down     # stops everything
```

The same URLs apply: the app on http://localhost:4200, the API on http://localhost:8080 and the inbox on http://localhost:8025. Data is stored in an H2 file database in `.dev/db`, and `scripts/dev.sh reset` deletes it. `scripts/dev.sh mail-stop` simulates an SMTP outage, to watch the email retries. [docs/manual-testing.md](docs/manual-testing.md) walks through 17 scenarios on the demo data.

### Running the tests

```bash
# Backend: JDK 21 required (macOS: export JAVA_HOME=$(/usr/libexec/java_home -v 21))
cd backend
mvn test        # unit tests, a few seconds
mvn verify      # unit + integration tests + coverage gate; PostgresIT needs Docker running

# Frontend
cd frontend/frontend/fops-frontend
npm ci
npm run test:ci         # unit tests
npm run test:coverage   # unit tests + coverage gate
npm run e2e             # Playwright on the installed Google Chrome; starts backend and frontend if not running
```

[docs/testing.md](docs/testing.md) describes every layer of the suite.

## Domain model

The challenge supplied a Play Framework boilerplate with four entities:

| Entity | Provided attributes |
|---|---|
| Item | name |
| InventoryMovement | creationDate, item, quantity |
| Order | creationDate, item, quantity, user |
| User | name, email |

**The gap.** Nothing in that model links an order to the stock that fulfils it. A movement knows its item and quantity but not which order it served; an order knows how much it asked for but not how much it has received, or from where. So the model cannot answer three of the things the challenge asks for: which movements completed an order and which orders a movement served, how complete an order is, and, once an allocation has been made, how to undo it.

**How it is closed.** An allocation is not a separate table: it *is* a movement. Every unit given to an order is an `OUT` movement that carries two links:

- `order_id`, mandatory on every `OUT`: the order this stock was given to;
- `source_movement_id`: the `IN` movement (a delivery, or stock returned by a cancellation) whose units fed this allocation. It is empty when the order was served from stock already on hand at the moment it was created.

An order's movements answer "which deliveries completed this order" (`GET /api/orders/{id}/movements`), and an `IN` movement's allocations answer "which orders did this delivery serve" (`GET /api/inventory/movements/{id}`). The `OUT` movement that brings an order to 100% is flagged `completes_order`.

The entities as built:

| Entity | Attributes |
|---|---|
| User | name, email (unique) |
| Item | name, SKU (unique), stock on hand (`CHECK >= 0`) |
| Order | user, item, requested / fulfilled / remaining quantity, status (`PENDING`, `PARTIALLY_FULFILLED`, `COMPLETED`, `CANCELLED`), created / completed / cancelled timestamps |
| InventoryMovement | item, type (`IN` or `OUT`), quantity, order, source movement, completes-order flag, reason, timestamp |
| OrderNotification | one row per completed order (unique on `order_id`): delivery status, attempts, next attempt time, last error |

Completion is `fulfilled / requested`, and an order's quantities only change through allocation; there is no way to set them by hand. Orders are never edited or deleted: an order that is no longer wanted is cancelled (see below). Users and items can be deleted only while nothing references them.

**Repository layout.** The Play boilerplate (`app/`, `conf/`, `modules/`, `public/`, `test/`, its `Dockerfile` and `start.sh`) was removed from the repository root: the solution is built on Spring Boot and Angular, which the challenge allows ("feel free to use other tools"), and leaving an unused Play application at the root made it look like the entry point. Its four entities are the starting point described in the table above. The challenge statement itself is not part of the repository.

## How fulfillment works

**The ledger.** Every change to stock is a movement row, written in the same transaction as the change, and corrections are new rows, never edits. A movement's item, quantity and links cannot be modified after it is written; only its free-text reason can. An item's stock on hand is a stored counter, but nothing changes it without writing the movement that explains the change: initial stock is booked as an `IN` movement, and the item edit form has no stock field. So stock on hand always equals the sum of `IN` minus `OUT` movements for that item. The invariant simulation (see Testing) checks that equality after every step. There is one deliberate exception to append-only: an `IN` movement registered by mistake can be deleted, but only while none of its units have been allocated and all of them are still on hand. At that point no order depends on it.

**One lock per item.** Every operation that moves stock (creating an order, registering a delivery, cancelling an order) first takes a pessimistic row lock on the item (`SELECT … FOR UPDATE`) and does everything else inside that transaction. Two operations on the same item run one after the other; operations on different items do not wait for each other. `ConcurrencyIT` and `PostgresIT` verify that parallel orders and deliveries never oversell.

**FIFO across open orders.**

- When an order is created, it takes `min(stock on hand, quantity requested)` immediately.
- When stock arrives, the delivery is distributed across the item's open orders (`PENDING` and `PARTIALLY_FULFILLED`) oldest first, by creation time and then id. Each order takes as much as it still lacks until the stock runs out; whatever is left over stays on hand.

As a result, at most one order per item is partially fulfilled at any time, and it is always the oldest open one.

**Partial fulfillment.** An order that gets less than it asked for becomes `PARTIALLY_FULFILLED` and keeps its place in the queue. A `PENDING` order has received nothing yet. Neither needs any action: the next delivery for the item completes them by itself, through the same routine, and the order becomes `COMPLETED`.

**Cancellation by compensating movements.** `POST /api/orders/{id}/cancel` withdraws an open order; completed orders cannot be cancelled, because their email has already been sent. Nothing is deleted:

1. The order is marked `CANCELLED` first, so the routine no longer sees it as open.
2. Each of its `OUT` movements is returned to stock through a compensating `IN` movement linked to the cancelled order.
3. Each returned quantity is passed to the same FIFO routine a delivery uses, so the next orders in the queue receive it, and their allocations point to the exact return that fed them.

The cancelled order keeps a record of what it had received, and its trace shows both the allocations and the returns. A cancelled order leaves the open and completed counters on the dashboard and remains in the Orders list with its status, where it can be filtered for.

**Idempotent creation.** `POST /api/orders` and `POST /api/inventory/incoming` accept an optional `Idempotency-Key` header, stored in a unique `request_id` column. A client that retries after a timeout gets `200` with the original order or movement instead of a duplicate, and reusing a key with a different payload gets `409`. The unique constraint, not a prior lookup, is what decides, so two concurrent retries cannot both succeed.

**Completion email.** When an allocation brings an order to 100%, a notification row is written in the same transaction (a transactional outbox), so the email is recorded if and only if the completion commits. After the commit, an `@Async` listener (`@TransactionalEventListener(phase = AFTER_COMMIT)`) sends it, so SMTP is never contacted while the item lock is held, and a mail failure cannot roll back an allocation. If sending fails, a scheduler retries it, polling every 15 s. The retry delay doubles each time: 30 s, 60 s, 120 s, then 240 s. After the fifth failed attempt the notification is marked `FAILED`, and it can be retried by hand from the order (`POST /api/orders/{id}/notification/retry`). The unique constraint on the order guarantees a single email per order. All of these values are configurable.

## Testing

| Suite | Tests | Runs on |
|---|---|---|
| Backend unit (`*Test`) | 140 | JUnit 5 + Mockito, no Spring context |
| Backend integration (`*IT`) | 90 | Spring Boot on H2, plus `PostgresIT` on PostgreSQL 16 via Testcontainers (0 skipped) |
| Frontend unit | 121 | Vitest + Angular TestBed |
| End to end | 12 | Playwright, real browser against the real backend |

Backend coverage across unit and integration tests is 91.4% of lines and 87.1% of branches. The build fails below 80% / 75%.

### Invariant simulation

`FulfillmentSimulationTest` runs the real services on in-memory repositories: 25 seeded runs of 400 random operations each, covering orders, deliveries and cancellations across several items. After **every** step it checks the business invariants:

- stock equals the ledger and is never negative;
- no order is over-allocated, and every order's fulfilled quantity equals its `OUT` movements;
- an item with stock has no waiting orders;
- FIFO: only the oldest open order can be partial;
- a cancelled order returned everything it had received;
- exactly one email per completed order.

A failure names the seed and step that reproduce it.

**The suite was itself tested.** Passing tests only show that the code agrees with the tests. To measure whether the simulation actually detects broken business rules, real bugs were planted in production code, the simulation was run, and the code was restored:

| Injected bug | Repetitions that failed |
|---|---|
| Allocation serves the newest order first (breaks FIFO) | 25 of 25 |
| Cancellation marks the order CANCELLED after re-allocating (it receives its own returned stock) | 23 of 25 |

The 23 of 25 is not a flaw in the test, and it is reported as measured.

- **Why the FIFO bug fails every run.** It shows up whenever two orders for the same item wait at the same time, and every run produces that.
- **When the cancellation bug can show.** The cancelled order must already have received stock and still be the oldest open order for its item. Only then does the re-allocation hand it its own returned units. Since only the oldest open order can be partial, that means cancelling a partially fulfilled order.
- **Why two runs passed.** Their random sequence never cancelled a partially fulfilled order, so the bug never had a chance to act.

Seeded random exploration finds bugs with a probability, not with certainty. That is why the simulation complements the targeted tests instead of replacing them. `OrderCancellationServiceTest` (`returnedUnitsAreBackInStockBeforeTheRoutineRunsAndTheOrderIsAlreadyCancelled`) and `OrderCancellationIT` build that exact situation on purpose, so a bug like this one fails them every time. The simulation's value is the combinations nobody thought to write down.

### Integration tests on PostgreSQL

`@Testcontainers(disabledWithoutDocker = true)` turned a real `BadRequestException` (Testcontainers 1.19.8 asks for Docker API 1.32, which current engines reject) into a silent skip. The suite reported "3 skipped" instead of a failure, so the per-item pessimistic lock and the `CHECK` constraint were not verified on PostgreSQL until the API version was pinned to 1.41 in the Failsafe configuration. A skipped test is an invisible failure.

## Performance

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

The Orders, Inventory and Catalog pages, and the dashboard's Current inventory card, paginate (10 rows per page by default; 25, 50 or 100 on demand) and the pages filter in the browser. The Current inventory card pages after sorting by shortfall, so the items to reorder are always on its first page. Orders search `#order`, user name or email, and item name or SKU; Inventory searches item, SKU, reason, `#movement` and `#order`; Users search name and email; Items search name and SKU. The API keeps returning full result sets, so no endpoint contract changed late in the build. With 2,000 orders and 2,400 movements those lists respond in 10–22 ms; what needed fixing was rendering thousands of rows at once, and client-side paging solves exactly that. Server-side pagination is the step to take when result sets stop fitting comfortably in one response.

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

## What I would do next

- Testcontainers 1.19.8 defaults to Docker API 1.32 and does not negotiate; a newer version negotiates with the engine and would remove the pinned version.

## Out of scope

- No migration tool. The schema is generated by Hibernate from a fresh database; `docker compose up` from a clean clone produces the full schema. A production system would use Flyway or Liquibase — adding it here would have meant a new dependency for a schema that is created once.
- Server-side pagination. List pages paginate and search in the browser (see *List pages: client-side pagination and search* above), but the list endpoints return full result sets. With the data volumes this exercise produces that is not a bottleneck, and paginating on the server would have changed the shape of every list response and its frontend consumer late in the build. The indexes above are what keep the queries behind those endpoints from degrading.
