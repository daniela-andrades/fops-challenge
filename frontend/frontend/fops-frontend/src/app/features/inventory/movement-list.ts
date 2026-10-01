import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { InventoryMovement, Item, MovementType } from '../../core/models';
import { StatusBadge } from '../../shared/components/status-badge';
import { Paginator } from '../../shared/components/paginator';
import { DEFAULT_PAGE_SIZE, matchesSearch, pageOf } from '../../shared/pagination';

@Component({
  selector: 'app-movement-list',
  imports: [FormsModule, RouterLink, DatePipe, StatusBadge, Paginator],
  template: `
    <section class="page">
      <header class="page-header">
        <div>
          <p class="eyebrow">Traceability</p>
          <h1>Inventory movements</h1>
        </div>
        <span class="muted">{{ visible().length }} movements</span>
      </header>

      <section class="panel">
        <div class="filters">
          <select [(ngModel)]="itemId" (ngModelChange)="load()" name="itemId" aria-label="Filter by item">
            <option [ngValue]="null">All items</option>
            @for (item of items(); track item.id) {
              <option [ngValue]="item.id">{{ item.name }} · {{ item.sku }}</option>
            }
          </select>
          <select [ngModel]="type()" (ngModelChange)="type.set($event); page.set(1)" name="type" aria-label="Filter by type">
            <option [ngValue]="null">In and out</option>
            <option ngValue="IN">Incoming (IN)</option>
            <option ngValue="OUT">Allocations (OUT)</option>
          </select>
          <input type="search" [ngModel]="search()" (ngModelChange)="search.set($event); page.set(1)" name="search"
                 placeholder="Search item, SKU, reason, #movement or #order" aria-label="Search movements" />
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th><th>Type</th><th>Item</th><th class="num">Qty</th>
                <th>Order</th><th>Fed by</th><th>Reason</th><th>Date</th>
              </tr>
            </thead>
            <tbody>
              @for (movement of pagedMovements(); track movement.id) {
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
                  <td>
                    @if (movement.sourceMovementId) {
                      <a [routerLink]="['/inventory/movements', movement.sourceMovementId]">IN #{{ movement.sourceMovementId }}</a>
                    } @else { <span class="muted">—</span> }
                  </td>
                  <td>{{ movement.reason }}</td>
                  <td class="muted">{{ movement.createdAt | date: 'short' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="8" class="empty">No movements recorded.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <app-paginator [total]="visible().length" [(page)]="page" [(pageSize)]="pageSize" />
      </section>
    </section>
  `
})
export class MovementListPage implements OnInit {
  private readonly api = inject(ApiService);

  readonly items = signal<Item[]>([]);
  readonly movements = signal<InventoryMovement[]>([]);
  readonly type = signal<MovementType | null>(null);
  readonly search = signal('');
  readonly page = signal(1);
  readonly pageSize = signal(DEFAULT_PAGE_SIZE);
  itemId: number | null = null;

  private readonly itemsById = computed(() => new Map(this.items().map((item) => [item.id, item])));
  readonly visible = computed(() => {
    const type = this.type();
    return [...this.movements()]
      .reverse()
      .filter((movement) => !type || movement.movementType === type)
      .filter((movement) => {
        const item = this.itemsById().get(movement.itemId);
        return matchesSearch(this.search(), item?.name, item?.sku, movement.reason,
          `#${movement.id}`, movement.orderId ? `#${movement.orderId}` : null);
      });
  });
  readonly pagedMovements = computed(() => pageOf(this.visible(), this.page(), this.pageSize()));

  ngOnInit(): void {
    this.api.getItems().subscribe((items) => this.items.set(items));
    this.load();
  }

  load(): void {
    this.page.set(1);
    this.api.getMovements(this.itemId).subscribe((movements) => this.movements.set(movements));
  }

  itemName(itemId: number): string {
    return this.itemsById().get(itemId)?.name ?? `Item #${itemId}`;
  }
}
