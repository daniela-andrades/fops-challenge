import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Item, Order, OrderFilters, OrderStatus, User } from '../../core/models';
import { ProgressBar } from '../../shared/components/progress-bar';
import { StatusBadge } from '../../shared/components/status-badge';

@Component({
  selector: 'app-order-list',
  imports: [FormsModule, RouterLink, DatePipe, ProgressBar, StatusBadge],
  template: `
    <section class="page">
      <header class="page-header">
        <div>
          <p class="eyebrow">Fulfillment</p>
          <h1>Orders</h1>
        </div>
        <span class="muted">{{ orders().length }} orders</span>
      </header>

      <section class="panel">
        <div class="filters">
          <select [(ngModel)]="filters.userId" (ngModelChange)="load()" name="userId" aria-label="Filter by user">
            <option [ngValue]="null">All users</option>
            @for (user of users(); track user.id) {
              <option [ngValue]="user.id">{{ user.name }}</option>
            }
          </select>
          <select [(ngModel)]="filters.itemId" (ngModelChange)="load()" name="itemId" aria-label="Filter by item">
            <option [ngValue]="null">All items</option>
            @for (item of items(); track item.id) {
              <option [ngValue]="item.id">{{ item.name }}</option>
            }
          </select>
          <select [(ngModel)]="filters.status" (ngModelChange)="load()" name="status" aria-label="Filter by status">
            <option [ngValue]="null">All statuses</option>
            @for (status of statuses; track status.value) {
              <option [ngValue]="status.value">{{ status.label }}</option>
            }
          </select>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Order</th><th>User</th><th>Item</th><th>Status</th>
                <th class="num">Requested</th><th class="num">Fulfilled</th><th class="num">Remaining</th>
                <th>Completion</th><th>Created</th>
              </tr>
            </thead>
            <tbody>
              @for (order of orders(); track order.id) {
                <tr class="clickable" (click)="open(order.id)">
                  <td><a [routerLink]="['/orders', order.id]">#{{ order.id }}</a></td>
                  <td>{{ userName(order.userId) }}</td>
                  <td>{{ itemName(order.itemId) }}</td>
                  <td><app-status-badge [value]="order.status" /></td>
                  <td class="num">{{ order.requestedQuantity }}</td>
                  <td class="num">{{ order.fulfilledQuantity }}</td>
                  <td class="num">{{ order.remainingQuantity }}</td>
                  <td><app-progress-bar [percent]="order.completionPercent" [status]="order.status" /></td>
                  <td class="muted">{{ order.createdAt | date: 'short' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="9" class="empty">No orders match these filters.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </section>
  `
})
export class OrderListPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly orders = signal<Order[]>([]);
  readonly users = signal<User[]>([]);
  readonly items = signal<Item[]>([]);
  private readonly usersById = computed(() => new Map(this.users().map((user) => [user.id, user])));
  private readonly itemsById = computed(() => new Map(this.items().map((item) => [item.id, item])));

  readonly statuses: { value: OrderStatus; label: string }[] = [
    { value: 'PENDING', label: 'Pending' },
    { value: 'PARTIALLY_FULFILLED', label: 'Partially fulfilled' },
    { value: 'COMPLETED', label: 'Completed' }
  ];

  filters: OrderFilters = { userId: null, itemId: null, status: null };

  ngOnInit(): void {
    forkJoin({ users: this.api.getUsers(), items: this.api.getItems() }).subscribe(({ users, items }) => {
      this.users.set(users);
      this.items.set(items);
    });
    this.load();
  }

  load(): void {
    this.api.getOrders(this.filters).subscribe((orders) => this.orders.set(orders));
  }

  open(orderId: number): void {
    this.router.navigate(['/orders', orderId]);
  }

  userName(userId: number): string {
    return this.usersById().get(userId)?.name ?? `User #${userId}`;
  }

  itemName(itemId: number): string {
    return this.itemsById().get(itemId)?.name ?? `Item #${itemId}`;
  }
}
