import { Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { InventoryMovementDetail } from '../../core/models';
import { ProgressBar } from '../../shared/components/progress-bar';
import { StatusBadge } from '../../shared/components/status-badge';

@Component({
  selector: 'app-movement-detail',
  imports: [RouterLink, DatePipe, ProgressBar, StatusBadge],
  template: `
    <section class="page">
      <a routerLink="/inventory">← Back to movements</a>

      @if (movement(); as m) {
        <header class="page-header">
          <div>
            <p class="eyebrow">Movement #{{ m.id }}</p>
            <h1>{{ m.movementType === 'IN' ? 'Incoming stock' : 'Allocation to order' }}</h1>
          </div>
          <app-status-badge [value]="m.movementType" />
        </header>

        <section class="panel">
          <dl class="facts">
            <div><dt>Item</dt><dd>{{ m.itemName }} <span class="muted">· {{ m.itemSku }}</span></dd></div>
            <div><dt>Quantity</dt><dd>{{ m.quantity }}</dd></div>
            <div><dt>Date</dt><dd>{{ m.createdAt | date: 'medium' }}</dd></div>
            <div><dt>Reason</dt><dd>{{ m.reason || '—' }}</dd></div>
            @if (m.movementType === 'OUT') {
              <div>
                <dt>Stock came from</dt>
                <dd>
                  @if (m.sourceMovementId) {
                    <a [routerLink]="['/inventory/movements', m.sourceMovementId]">IN #{{ m.sourceMovementId }}</a>
                  } @else {
                    <span class="muted">Stock on hand when the order was created</span>
                  }
                </dd>
              </div>
              <div>
                <dt>Completed the order</dt>
                <dd>@if (m.completesOrder) { <span class="tag">Yes</span> } @else { No }</dd>
              </div>
            }
          </dl>
        </section>

        @if (m.order; as order) {
          <section class="panel">
            <h2>Associated order</h2>
            <div class="order-row">
              <a [routerLink]="['/orders', order.id]">Order #{{ order.id }}</a>
              <app-status-badge [value]="order.status" />
              <span class="muted">{{ order.fulfilledQuantity }} / {{ order.requestedQuantity }}</span>
              <app-progress-bar [percent]="order.completionPercent" [status]="order.status" />
            </div>
          </section>
        }

        @if (m.movementType === 'IN') {
          <section class="panel">
            <h2>Where this stock went</h2>
            <p class="muted hint">{{ allocatedUnits() }} of {{ m.quantity }} units went to orders waiting on arrival · {{ m.quantity - allocatedUnits() }} added to stock on hand.</p>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr><th>Allocation</th><th>Order</th><th class="num">Qty</th><th></th></tr>
                </thead>
                <tbody>
                  @for (a of m.allocations; track a.id) {
                    <tr>
                      <td><a [routerLink]="['/inventory/movements', a.id]">OUT #{{ a.id }}</a></td>
                      <td><a [routerLink]="['/orders', a.orderId]">#{{ a.orderId }}</a></td>
                      <td class="num">{{ a.quantity }}</td>
                      <td>@if (a.completesOrder) { <span class="tag">Completed the order</span> }</td>
                    </tr>
                  } @empty {
                    <tr><td colspan="4" class="empty">No open orders were waiting for this item.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
        }
      } @else if (notFound()) {
        <p class="empty">Movement not found.</p>
      } @else {
        <p class="empty">Loading…</p>
      }
    </section>
  `,
  styles: `
    .order-row { display: flex; align-items: center; flex-wrap: wrap; gap: 12px 16px; }
    .order-row app-progress-bar { flex: 1; min-width: 180px; }
    .hint { margin: -4px 0 4px; font-size: .88rem; }
  `
})
export class MovementDetailPage {
  private readonly api = inject(ApiService);

  readonly id = input.required({ transform: numberAttribute });
  readonly movement = signal<InventoryMovementDetail | null>(null);
  readonly notFound = signal(false);
  readonly allocatedUnits = computed(() =>
    (this.movement()?.allocations ?? []).reduce((sum, allocation) => sum + allocation.quantity, 0)
  );

  constructor() {
    effect(() => {
      const id = this.id();
      this.movement.set(null);
      this.notFound.set(false);
      this.api.getMovement(id).subscribe({
        next: (movement) => this.movement.set(movement),
        error: () => this.notFound.set(true)
      });
    });
  }
}
