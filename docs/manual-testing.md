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
| Create user with email `ana@` | "Email is not a valid address" |
| Create item with initial stock -1 | "Initial stock cannot be negative" |
| Open http://localhost:4200/orders/999 | "Order 999 not found", and the page says *Order not found.* |

**Also check:** *Create order* stays disabled until both a user and an item are selected, and forms keep their values after an error.

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
