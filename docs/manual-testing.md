# Manual testing guide

A walkthrough of every business rule, run against the local environment without Docker. Each scenario lists the steps and what you should see. The scenarios build on each other, so run them in order after a fresh seed.

## Setup

```bash
scripts/dev.sh reset   # optional: start from an empty database
scripts/dev.sh up      # builds the backend and starts backend, frontend and dev inbox
scripts/dev.sh seed    # loads the demo data below
```

| What | URL |
|---|---|
| Application | http://localhost:4200 |
| Dev inbox (emails) | http://localhost:8025 |
| API | http://localhost:8080/api |
| Database console | http://localhost:8080/h2-console (JDBC URL is printed by `dev.sh up`, user `sa`, no password) |

The scenarios build on each other; 13 to 17 can run at any point after the seed.

Other commands: `scripts/dev.sh status`, `down`, `logs backend|frontend|mail`, `mail-stop`, `mail-start`.

For manual testing, email retries are shortened to 10 s, 20 s, 40 s and 4 attempts. Production defaults are 30 s base delay and 5 attempts.

### Demo data after `seed`

| Item | SKU | Stock | Orders |
|---|---|---|---|
| Laptop | LAP-001 | 15 | #1 Ana, 5 units, **Completed** from stock |
| Monitor | MON-001 | 0 | #2 Luis, 8 units, **Partial** 5/8 |
| Keyboard | KEY-001 | 0 | #3 Marta, 4 units, **Partial** 3/4 (fed by incoming movement #6)<br>#4 Ana, 6 units, **Pending**, queued after #3 |
| Mouse | MOU-001 | 50 | none |

Users: Ana Garcia (`ana@fops.local`), Luis Perez (`luis@fops.local`), Marta Ruiz (`marta@fops.local`).

---

## 1. Dashboard overview

1. Open http://localhost:4200.

**Expected**
- Open orders **3** (1 pending · 2 partial).
- Completed orders **1** (1 email sent).
- Open demand **10** (3 monitors + 1 + 6 keyboards).
- Stock on hand **65** (4 items · 2 out of stock).
- Users **3**.
- *Open orders* lists #2, #3 and #4 with progress bars. *Latest movements* is newest first.
- The dev inbox has one email: "Order #1 completed", to ana@fops.local.

## 2. Order completed from stock

1. Create order: Luis, Mouse, quantity 10.

**Expected**
- Toast: "Order #5 created · 100% fulfilled (completed)".
- Mouse stock drops to 40.
- Within a few seconds the inbox shows "Order #5 completed" to luis@fops.local, with dates shown as `yyyy-MM-dd HH:mm`.
- Orders → #5: Completion 100%. Covered by 1 movement · single shipment. History row shows *Stock on hand* as the source and *Completed the order*. Completion email is **Sent**.

## 3. Partial fulfillment

1. Create order: Marta, Laptop, quantity 20 (only 15 in stock).

**Expected**
- Toast: "… 75% fulfilled (partially fulfilled)".
- Laptop stock becomes 0.
- No email.
- The order detail says the email will be sent at 100%.

## 4. Pending order

1. Create order: Ana, Monitor, quantity 2 (stock 0).

**Expected**
- Toast: "… 0% fulfilled (pending)".
- The order history reads "No stock allocated yet".
- No new OUT movement in Inventory.

## 5. Incoming stock is allocated oldest first (FIFO)

1. Incoming inventory: Keyboard, quantity 7, reason "Supplier B".

**Expected**
- Toast: "7 units of KEY-001 received · allocated to 2 order(s), 2 completed".
- Order #3 is Completed 4/4 with **two** history rows: 3 from IN #6 and 1 from the new IN. Its detail says "several shipments".
- Order #4 is Completed 6/6, from the new IN only.
- Keyboard stock is 0. Two new emails, to marta@ and ana@.
- **Variation:** Incoming Monitor, quantity 10. The oldest open monitor order (#2, missing 3) is served first, then Ana's order from scenario 4 (2). The remaining 5 stay in stock.

## 6. Traceability both ways

1. From order #3, click each **IN #…** link in the history.
2. On an IN movement, check *Where this stock went*.
3. Inventory → filter by *Allocations (OUT)*, then open any OUT movement.

**Expected**
- *Where this stock went* lists the OUT allocations and orders it fed, plus how many units were added to stock on hand.
- An OUT movement shows its order with its progress bar, *Stock came from*, and whether it completed the order.
- Inventory filters by item (server side) and by type (client side).

## 7. Email delivery when SMTP is down

1. `scripts/dev.sh mail-stop`
2. Create order: Luis, Mouse, quantity 1, and open its detail page.
3. Watch the *Completion email* panel for about 20 s. The page refreshes by itself.
4. `scripts/dev.sh mail-start`

**Expected**
- Before restarting: **Retrying**, attempts increasing, last error "MailSendException: Connection refused", and a next attempt time.
- Within one retry interval after restarting: **Sent**, and the email appears in the inbox exactly once.

**Exhausting retries**
1. `scripts/dev.sh mail-stop`, then complete another Mouse order.
2. Wait about 80 s (attempts at 0 s, +10 s, +20 s, +40 s).

**Expected**
- The order shows **Failed** with the last error and a **Retry now** button.
- The dashboard card reads "… · 1 failed" in red.
- Clicking **Retry now** while mail is still down shows an error toast.
- After `scripts/dev.sh mail-start`, **Retry now** shows "Email sent to …" and the email arrives once.

## 8. Validation and error messages

| Action | Expected toast |
|---|---|
| Create order with quantity 0 | "Quantity must be greater than 0" |
| Create item with SKU `lap-001` (lowercase) | "An item with SKU already exists: LAP-001" |
| Create user with email `ANA@fops.local` | "A user with this email already exists: ana@fops.local" |
| Create user with email `ana@` | **Save user** stays disabled. The backend still validates it: `curl -s -X POST localhost:8080/api/users -H 'Content-Type: application/json' -d '{"name":"Ana","email":"ana@"}'` returns 400 "Email is not a valid address" |
| Create item with initial stock -1 | **Save item** stays disabled. Through the API (`stockOnHand: -1`) the backend returns 400 "Initial stock cannot be negative" |
| Open http://localhost:4200/orders/999 | "Order 999 not found", and the page says *Order not found.* |

**Also check:**
- *Create order* and *Register stock* stay disabled until a user and/or an item are selected.
- *Save item* stays disabled until there is a name and a SKU and the initial stock is not negative.
- *Save user* stays disabled until there is a name and a valid email.
- Forms keep their values after an error.

## 9. Order filters

On Orders, combine the user, item and status filters.

**Expected:** the list and the count update immediately. A combination with no results shows "No orders match these filters."

## 10. Data integrity in the database

Open the H2 console, connect with the JDBC URL printed by `dev.sh up`, and run:

```sql
-- Stock must equal the ledger (IN minus OUT) for every item: expect no rows
SELECT i.sku, i.stock_on_hand,
       SUM(CASE WHEN m.movement_type = 'IN' THEN m.quantity ELSE -m.quantity END) AS ledger
FROM items i LEFT JOIN inventory_movements m ON m.item_id = i.id
GROUP BY i.id, i.sku, i.stock_on_hand
HAVING i.stock_on_hand <> COALESCE(SUM(CASE WHEN m.movement_type = 'IN' THEN m.quantity ELSE -m.quantity END), 0);

-- Every OUT movement is linked to an order: expect 0
SELECT COUNT(*) FROM inventory_movements WHERE movement_type = 'OUT' AND order_id IS NULL;

-- Exactly one email record per completed order
SELECT o.id, o.status, n.status AS email, n.attempts
FROM orders o LEFT JOIN order_email_notifications n ON n.order_id = o.id
ORDER BY o.id;

-- The database itself rejects negative stock: expect a check constraint error
UPDATE items SET stock_on_hand = -1 WHERE sku = 'MOU-001';
```

## 11. Persistence and reset

1. `scripts/dev.sh down`, then `scripts/dev.sh up`.
2. `scripts/dev.sh reset && scripts/dev.sh up && scripts/dev.sh seed`

**Expected**
- After step 1, all orders, movements and inbox emails are still there.
- After step 2, you are back to the initial demo state.

## 12. Layout

Narrow the browser to phone width (or use device mode in DevTools).

**Expected**
- No horizontal page scroll.
- KPI cards wrap two per row, and tables scroll inside their panel.
- Navigation stays usable.

## 13. Catalog maintenance (update and delete)

1. Open **Catalog**, click **Edit** on Luis, change his email to `LUIS.P@fops.local` and save.
2. Try changing Marta's email to `ana@fops.local`.
3. Click **Delete** on Ana and confirm.
4. Create a user on the dashboard, then delete them from the Catalog. Cancel once in the dialog before confirming.
5. Edit the Mouse item: rename it and change its SKU to `mou-002`.
6. Try deleting Laptop. Then create an item with 0 stock on the dashboard and delete it.

**Expected**
- Step 1: the email is saved lowercased, and Luis's orders keep pointing to him.
- Step 2: "A user with this email already exists: ana@fops.local".
- Step 3: "User 1 has orders and cannot be deleted". Users with orders are kept, because their orders and emails depend on them.
- Step 4: cancelling (the button, a click outside the dialog, or Escape) changes nothing. Confirming removes the user.
- Step 5: the SKU is saved uppercased and the stock is unchanged. Stock is never editable here.
- Step 6: Laptop is refused ("…has orders or inventory movements and cannot be deleted"). The unused item is deleted.

## 14. Movement corrections

1. Register incoming stock for Mouse (quantity 30, reason "Typo") and open that movement from Inventory.
2. Edit its reason to "Duplicated delivery note".
3. Click **Delete movement** and confirm.
4. Open incoming movement #6 (the keyboards that fed order #3), and any OUT movement.
5. Register incoming stock for an item, create an order that consumes it all, then try deleting that incoming movement through the API:
   `curl -X DELETE localhost:8080/api/inventory/movements/<id>`

**Expected**
- Step 2: the reason updates. Quantity and item cannot be edited, because they are the ledger.
- Step 3: the page returns to Inventory with "Movement #… deleted", and Mouse stock drops back by 30.
- Step 4: neither offers deletion. #6 says its stock was already allocated to orders; the OUT movement says it only changes through its order.
- Step 5: the API returns 422 "…only 0 of its N units are still in stock".
- In the database console, the ledger query from scenario 10 still returns no rows.

---

## 15. Idempotent creation (Idempotency-Key)

`POST /api/orders` and `POST /api/inventory/incoming` accept an optional `Idempotency-Key` header. A retry with the same key returns the original resource instead of creating it again. Run from a terminal (Luis = user 2, Mouse = item 4):

```bash
KEY=$(uuidgen)
# 1. The same order twice
curl -s -w ' -> %{http_code}\n' -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
     -H "Idempotency-Key: $KEY" -d '{"userId":2,"itemId":4,"requestedQuantity":1}'
curl -s -w ' -> %{http_code}\n' -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
     -H "Idempotency-Key: $KEY" -d '{"userId":2,"itemId":4,"requestedQuantity":1}'
# 2. Same key, different quantity
curl -s -w ' -> %{http_code}\n' -X POST localhost:8080/api/orders -H 'Content-Type: application/json' \
     -H "Idempotency-Key: $KEY" -d '{"userId":2,"itemId":4,"requestedQuantity":9}'
# 3. A delivery retried with its own key
DKEY=$(uuidgen)
for i in 1 2; do curl -s -o /dev/null -w 'delivery -> %{http_code}\n' -X POST localhost:8080/api/inventory/incoming \
     -H 'Content-Type: application/json' -H "Idempotency-Key: $DKEY" -d '{"itemId":4,"quantity":5,"reason":"Retry test"}'; done
```

**Expected**
- Step 1: the first call returns `201`, the second `200` with the **same order id**. Mouse stock drops by 1 only once, and Luis receives a single email in the inbox.
- Step 2: `409` "Idempotency-Key … was already used for a different request: requestedQuantity 1 stored, 9 requested". No new order.
- Step 3: `201` then `200`. Mouse stock goes up by 5 only once, and Inventory shows a single "Retry test" movement.

**In the UI**
1. Open DevTools → Network.
2. Create an order from the dashboard.
3. Press Enter twice quickly on another order.

**Expected in the UI**
- The `POST /api/orders` request carries an `Idempotency-Key` header, and the next order uses a different one.
- The double Enter creates a single order, because the button stays disabled while the request is in flight.

## 16. Order cancellation

The setup uses its own items, so this works at any point.

1. Dashboard → **Create item**: "Cable", SKU `CAB-001`, initial stock 4.
2. **Create order**: Luis, Cable, quantity 6.
3. **Create order**: Marta, Cable, quantity 3.
4. Orders → open Luis's Cable order → **Cancel order** → read the dialog → confirm.
5. Check Marta's Cable order, the inbox, Inventory filtered by Cable, and the Orders list (also filter by status *Cancelled*).
6. Create an item "Adapter" with stock 0, then an order for Ana for 2. Open it and **Cancel order**.
7. Open Marta's Cable order (now completed).
8. From a terminal, cancel that completed order, then cancel Luis's order a second time:
   `curl -s -X POST localhost:8080/api/orders/<id>/cancel`

**Expected**
- Step 2: "… 66.67% fulfilled (partially fulfilled)", and Cable stock goes to 0.
- Step 3: "… 0% fulfilled (pending)".
- Step 4: the dialog says *"Cancelling will return 4 units to stock. They may be automatically allocated to other pending orders."* After confirming:
  - the toast reads "Order #… cancelled · 4 units returned to stock";
  - the badge reads **Cancelled**, with a note that the 4 allocated units were returned;
  - the email panel says *Cancelled orders are not notified*;
  - the Cancel button disappears.
- Step 5:
  - Marta's order is **Completed** 3/3, fed by the returned stock, and her completion email arrives. The cancelled order sends no email.
  - Cable stock is 1. Inventory shows, in order: IN 4 *Initial stock*, OUT 4 (Luis), IN 4 *Returned to stock - order #… cancelled* (linked to Luis's order), OUT 3 (Marta) whose *Fed by* is that return.
  - The original OUT 4 is unchanged; corrections are always new rows.
  - Luis's order stays in the Orders list as Cancelled. It does not appear in the dashboard's *Open orders* panel, and its progress still shows 4/6 with a single allocation.
- Step 6: the dialog says *"Nothing has been allocated to this order yet, so no stock is returned."* The order is cancelled and no movement is created.
- Step 7: completed orders show no Cancel button.
- Step 8: `409` "…already completed and its notification was sent…" for Marta's order, and `409` "Order … is already cancelled" for Luis's.
- The ledger query from scenario 10 still returns no rows.

---

## 17. Long lists: pagination and search

Load volume first (on top of the current data; reset afterwards to go back to the demo):

```bash
python3 scripts/seed_bulk_data.py      # 20 users, 50 items, 2,000 orders, 1,000 deliveries, 60 cancellations
```

1. **Inventory:** check the range and pages (10 rows by default), go to the next page, change rows per page to 100.
2. **Inventory:** type `BULK-007` in the search box, then `#2000`, then `Initial stock`. Combine with the type filter *Allocations (OUT)*.
3. **Orders:** filter by status *Pending* and page through the results; then search `#2000`, `bulk05@` and `BULK-007`, alone and combined with the filters.
4. **Catalog:** search users for `bulk1` and items for `bulk-04`; page through each table independently.

**Expected**
- Each table shows at most the selected number of rows (10 by default), with "1–10 of 2,410" style ranges and *Page X of Y*. Prev and Next are disabled at the ends.
- Changing rows per page, a filter or the search text goes back to page 1, and the header count shows the filtered total.
- Searches are case-insensitive. `#2000` finds movement 2000 and the movements of order 2000. A search with no match says so instead of showing an empty table.
- The Users and Items pagers in the Catalog are independent of each other.

To return to the demo data: `scripts/dev.sh reset && scripts/dev.sh up && scripts/dev.sh seed`.

---

## Checklist

| # | Scenario | Result | Notes |
|---|---|---|---|
| 1 | Dashboard overview | ☐ | |
| 2 | Completed from stock + email | ☐ | |
| 3 | Partial fulfillment | ☐ | |
| 4 | Pending order | ☐ | |
| 5 | FIFO allocation of incoming stock | ☐ | |
| 6 | Traceability order ↔ movement | ☐ | |
| 7 | Email retry and manual retry | ☐ | |
| 8 | Validation messages | ☐ | |
| 9 | Order filters | ☐ | |
| 10 | Database integrity | ☐ | |
| 11 | Persistence and reset | ☐ | |
| 12 | Layout on small screens | ☐ | |
| 13 | Catalog maintenance (update and delete) | ☐ | |
| 14 | Movement corrections | ☐ | |
| 15 | Idempotent creation (Idempotency-Key) | ☐ | |
| 16 | Order cancellation | ☐ | |
| 17 | Long lists: pagination and search | ☐ | |
