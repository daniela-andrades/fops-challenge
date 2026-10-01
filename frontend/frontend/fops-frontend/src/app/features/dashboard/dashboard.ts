import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, forkJoin, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { newRequestId } from '../../core/request-id';
import { emailProblem, isValidEmail } from '../../core/email-validation';
import { DashboardSummary, InventoryMovement, Item, Order, User } from '../../core/models';
import { ProgressBar } from '../../shared/components/progress-bar';
import { StatusBadge } from '../../shared/components/status-badge';
import { Paginator } from '../../shared/components/paginator';
import { DEFAULT_PAGE_SIZE, pageOf } from '../../shared/pagination';

const STATUS_LABEL: Record<Order['status'], string> = {
  PENDING: 'pending',
  PARTIALLY_FULFILLED: 'partially fulfilled',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled'
};

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, RouterLink, DatePipe, ProgressBar, StatusBadge, Paginator],
  template: `
    <section class="page">
      <header class="page-header">
        <div>
          <p class="eyebrow">Fusion Operations</p>
          <h1>Inventory Command Center</h1>
        </div>
      </header>

      @if (summary(); as s) {
        <div class="grid stats">
          <article class="stat-card">
            <span>Open orders</span>
            <strong>{{ s.pendingOrders + s.partiallyFulfilledOrders }}</strong>
            <small>{{ s.pendingOrders }} pending · {{ s.partiallyFulfilledOrders }} partial</small>
          </article>
          <article class="stat-card">
            <span>Completed orders</span>
            <strong>{{ s.completedOrders }}</strong>
            <small>
              {{ s.notificationsSent }} emails sent
              @if (s.notificationsPending) { · {{ s.notificationsPending }} queued }
              @if (s.notificationsFailed) { · <span class="failed">{{ s.notificationsFailed }} failed</span> }
            </small>
          </article>
          <article class="stat-card">
            <span>Open demand</span>
            <strong>{{ s.openDemand }}</strong>
            <small>units waiting for stock</small>
          </article>
          <article class="stat-card">
            <span>Stock on hand</span>
            <strong>{{ s.totalStockOnHand }}</strong>
            <small>{{ s.totalItems }} items · {{ s.itemsOutOfStock }} out of stock</small>
          </article>
          <article class="stat-card">
            <span>Users</span>
            <strong>{{ s.totalUsers }}</strong>
          </article>
        </div>
      }

      <div class="grid">
        <form class="panel" (ngSubmit)="createOrder()">
          <h2>Create order</h2>
          <select [(ngModel)]="orderForm.userId" name="orderUserId" required>
            <option [ngValue]="null" disabled>Select user</option>
            @for (user of users(); track user.id) {
              <option [ngValue]="user.id">{{ user.name }} · {{ user.email }}</option>
            }
          </select>
          <select [(ngModel)]="orderForm.itemId" name="orderItemId" required>
            <option [ngValue]="null" disabled>Select item</option>
            @for (item of items(); track item.id) {
              <option [ngValue]="item.id">{{ item.name }} ({{ item.stockOnHand }} in stock)</option>
            }
          </select>
          <input type="number" [(ngModel)]="orderForm.requestedQuantity" name="requestedQuantity" min="1" placeholder="Requested quantity" required />
          <button type="submit" [disabled]="busy() || !orderForm.userId || !orderForm.itemId">Create order</button>
        </form>

        <form class="panel" (ngSubmit)="registerInventory()">
          <h2>Incoming inventory</h2>
          <select [(ngModel)]="inventoryForm.itemId" name="inventoryItemId" required>
            <option [ngValue]="null" disabled>Select item</option>
            @for (item of items(); track item.id) {
              <option [ngValue]="item.id">{{ item.name }} · {{ item.sku }}</option>
            }
          </select>
          <input type="number" [(ngModel)]="inventoryForm.quantity" name="inventoryQty" min="1" placeholder="Quantity" required />
          <input type="text" [(ngModel)]="inventoryForm.reason" name="inventoryReason" placeholder="Reason (optional)" />
          <button type="submit" [disabled]="busy() || !inventoryForm.itemId">Register stock</button>
        </form>

        <form class="panel" (ngSubmit)="createItem()">
          <h2>Create item</h2>
          <input type="text" [(ngModel)]="itemForm.name" name="itemName" placeholder="Item name" required />
          <input type="text" [(ngModel)]="itemForm.sku" name="itemSku" placeholder="SKU" required />
          <input type="number" [(ngModel)]="itemForm.stockOnHand" name="stockOnHand" min="0" placeholder="Initial stock" />
          <button type="submit" [disabled]="busy() || !canSaveItem()">Save item</button>
        </form>

        <form class="panel" (ngSubmit)="createUser()">
          <h2>Create user</h2>
          <input type="text" [(ngModel)]="userForm.name" name="userName" placeholder="Name" required />
          <input type="email" [(ngModel)]="userForm.email" name="userEmail" placeholder="Email" required
                 [class.invalid]="!!emailError()" [attr.aria-invalid]="!!emailError()" aria-describedby="user-email-error" />
          @if (emailError(); as problem) {
            <p class="field-error" id="user-email-error" role="alert">{{ problem }}</p>
          }
          <button type="submit" [disabled]="busy() || !canSaveUser()">Save user</button>
        </form>
      </div>

      <div class="grid two">
        <section class="panel">
          <div class="panel-title">
            <h2>Open orders</h2>
            <a routerLink="/orders">All orders →</a>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr><th>Order</th><th>Item</th><th>Status</th><th>Completion</th></tr>
              </thead>
              <tbody>
                @for (order of openOrders(); track order.id) {
                  <tr class="clickable" (click)="openOrder(order.id)">
                    <td><a [routerLink]="['/orders', order.id]">#{{ order.id }}</a></td>
                    <td>{{ itemName(order.itemId) }}</td>
                    <td><app-status-badge [value]="order.status" /></td>
                    <td><app-progress-bar [percent]="order.completionPercent" [status]="order.status" /></td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="empty">No open orders. Everything is fulfilled.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel">
          <h2>Current inventory</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr><th>Item</th><th>SKU</th><th class="num">Stock</th><th class="num">Demand</th></tr>
              </thead>
              <tbody>
                @for (row of pagedInventoryRows(); track row.item.id) {
                  <tr [class.short]="row.shortfall > 0">
                    <td>{{ row.item.name }}</td>
                    <td>{{ row.item.sku }}</td>
                    <td class="num">{{ row.item.stockOnHand }}</td>
                    <td class="num demand">
                      {{ row.demand }}
                      @if (row.shortfall > 0) {
                        <span class="shortfall" title="Units to reorder: demand minus stock">short {{ row.shortfall }}</span>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="empty">No items yet.</td></tr>
                }
              </tbody>
            </table>
          </div>
          <app-paginator [total]="inventoryRows().length" [(page)]="inventoryPage" [(pageSize)]="inventoryPageSize" />
        </section>
      </div>

      <section class="panel">
        <div class="panel-title">
          <h2>Latest movements</h2>
          <a routerLink="/inventory">Full history →</a>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>#</th><th>Type</th><th>Item</th><th class="num">Qty</th><th>Order</th><th>Reason</th><th>Date</th></tr>
            </thead>
            <tbody>
              @for (movement of latestMovements(); track movement.id) {
                <tr>
                  <td><a [routerLink]="['/inventory/movements', movement.id]">#{{ movement.id }}</a></td>
                  <td><app-status-badge [value]="movement.movementType" /></td>
                  <td>{{ itemName(movement.itemId) }}</td>
                  <td class="num">{{ movement.quantity }}</td>
                  <td>
                    @if (movement.orderId) {
                      <a [routerLink]="['/orders', movement.orderId]">#{{ movement.orderId }}</a>
                      @if (movement.completesOrder) { <span class="tag">completed it</span> }
                    } @else { <span class="muted">—</span> }
                  </td>
                  <td>{{ movement.reason }}</td>
                  <td class="muted">{{ movement.createdAt | date: 'short' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="empty">No movements yet.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </section>
  `,
  styles: `
    .panel-title { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
    form.panel h2 { text-align: center; }
    input.invalid { border-color: var(--danger); background: var(--danger-soft); }
    .field-error { margin: -4px 0 0; color: var(--danger); font-size: .82rem; font-weight: 600; }
    tr.short td { background: var(--warning-soft); }
    td.demand { white-space: nowrap; }
    .shortfall { margin-left: 6px; display: inline-flex; padding: 2px 8px; border-radius: 999px; font-size: .72rem; font-weight: 700; background: var(--danger-soft); color: var(--danger); }
    .tag { margin-left: 6px; }
    .failed { color: var(--danger); font-weight: 700; }
  `
})
export class DashboardPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);

  readonly summary = signal<DashboardSummary | null>(null);
  readonly users = signal<User[]>([]);
  readonly items = signal<Item[]>([]);
  readonly orders = signal<Order[]>([]);
  readonly movements = signal<InventoryMovement[]>([]);
  readonly busy = signal(false);

  readonly openOrders = computed(() =>
    this.orders().filter((order) => order.status === 'PENDING' || order.status === 'PARTIALLY_FULFILLED').slice(0, 10)
  );
  readonly latestMovements = computed(() => [...this.movements()].reverse().slice(0, 10));
  /**
   * Current inventory rows with outstanding demand and shortfall (units to reorder), items needing stock first.
   * The sort is stable, so items without a shortfall keep their usual order.
   */
  readonly inventoryPage = signal(1);
  readonly inventoryPageSize = signal(DEFAULT_PAGE_SIZE);
  readonly inventoryRows = computed(() =>
    this.items()
      .map((item) => {
        const demand = item.outstandingDemand ?? 0;
        return { item, demand, shortfall: Math.max(0, demand - item.stockOnHand) };
      })
      .sort((a, b) => b.shortfall - a.shortfall)
  );
  readonly pagedInventoryRows = computed(() => pageOf(this.inventoryRows(), this.inventoryPage(), this.inventoryPageSize()));
  private readonly itemsById = computed(() => new Map(this.items().map((item) => [item.id, item])));

  userForm = { name: '', email: '' };
  itemForm = { name: '', sku: '', stockOnHand: 0 };
  orderForm = { userId: null as number | null, itemId: null as number | null, requestedQuantity: 1 };
  /** Idempotency-Key of the order being filled in: kept across retries, renewed only after a successful submit. */
  orderRequestId = newRequestId();
  inventoryForm = { itemId: null as number | null, quantity: 1, reason: '' };

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    forkJoin({
      summary: this.api.getDashboardSummary(),
      users: this.api.getUsers(),
      items: this.api.getItems(),
      orders: this.api.getOrders(),
      movements: this.api.getMovements()
    }).subscribe((data) => {
      this.summary.set(data.summary);
      this.users.set(data.users);
      this.items.set(data.items);
      this.orders.set(data.orders);
      this.movements.set(data.movements);
    });
  }

  /** Name and SKU filled in; initial stock empty or not negative. The backend validates again. */
  canSaveItem(): boolean {
    const stock = this.itemForm.stockOnHand;
    const stockOk = stock === null || stock === undefined || (stock as unknown) === '' || Number(stock) >= 0;
    return !!this.itemForm.name?.trim() && !!this.itemForm.sku?.trim() && stockOk;
  }

  /** Live hint shown under the email field while what was typed is not a valid email. */
  emailError(): string | null {
    return emailProblem(this.userForm.email);
  }

  /** Name filled in and an email of the form name@domain.ext. The backend validates again. */
  canSaveUser(): boolean {
    return !!this.userForm.name?.trim() && isValidEmail(this.userForm.email);
  }

  createUser(): void {
    if (!this.canSaveUser()) {
      return;
    }
    this.run(this.api.createUser({ name: this.userForm.name, email: this.userForm.email }), (user) => {
      this.userForm = { name: '', email: '' };
      this.toasts.success(`User ${user.name} created`);
    });
  }

  createItem(): void {
    if (!this.canSaveItem()) {
      return;
    }
    const payload = { name: this.itemForm.name, sku: this.itemForm.sku, stockOnHand: Number(this.itemForm.stockOnHand) || 0 };
    this.run(this.api.createItem(payload), (item) => {
      this.itemForm = { name: '', sku: '', stockOnHand: 0 };
      this.toasts.success(`Item ${item.sku} created with ${item.stockOnHand} units`);
    });
  }

  createOrder(): void {
    const { userId, itemId, requestedQuantity } = this.orderForm;
    if (userId === null || itemId === null) {
      return;
    }
    this.run(this.api.createOrder({ userId, itemId, requestedQuantity: Number(requestedQuantity) }, this.orderRequestId), (order) => {
      this.orderForm = { userId: null, itemId: null, requestedQuantity: 1 };
      this.orderRequestId = newRequestId();
      this.toasts.success(`Order #${order.id} created · ${order.completionPercent}% fulfilled (${STATUS_LABEL[order.status]})`);
    });
  }

  registerInventory(): void {
    const { itemId, quantity, reason } = this.inventoryForm;
    if (itemId === null) {
      return;
    }
    const request = this.api
      .registerIncomingInventory({ itemId, quantity: Number(quantity), reason: reason.trim() })
      .pipe(switchMap((movement) => this.api.getMovement(movement.id)));

    this.run(request, (detail) => {
      this.inventoryForm = { itemId: null, quantity: 1, reason: '' };
      const completed = detail.allocations.filter((allocation) => allocation.completesOrder).length;
      const allocated = detail.allocations.length;
      this.toasts.success(
        `${detail.quantity} units of ${detail.itemSku} received` +
          (allocated ? ` · allocated to ${allocated} order(s), ${completed} completed` : ' · no open orders to allocate')
      );
    });
  }

  openOrder(orderId: number): void {
    this.router.navigate(['/orders', orderId]);
  }

  itemName(itemId: number): string {
    return this.itemsById().get(itemId)?.name ?? `Item #${itemId}`;
  }

  private run<T>(request: Observable<T>, onSuccess: (result: T) => void): void {
    if (this.busy()) {
      return; // a second submit (double Enter) before the button re-renders as disabled
    }
    this.busy.set(true);
    request.subscribe({
      next: (result) => {
        onSuccess(result);
        this.busy.set(false);
        this.loadData();
      },
      error: () => this.busy.set(false)
    });
  }
}
