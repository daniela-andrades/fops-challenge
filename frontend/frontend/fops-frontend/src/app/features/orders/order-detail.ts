import { Component, DestroyRef, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../core/toast.service';
import { OrderProgress } from '../../core/models';
import { ProgressBar } from '../../shared/components/progress-bar';
import { StatusBadge } from '../../shared/components/status-badge';

@Component({
  selector: 'app-order-detail',
  imports: [RouterLink, DatePipe, ProgressBar, StatusBadge],
  template: `
    <section class="page">
      <a routerLink="/orders">← Back to orders</a>

      @if (progress(); as p) {
        <header class="page-header">
          <div>
            <p class="eyebrow">Order #{{ p.orderId }}</p>
            <h1>{{ p.itemSku }} × {{ p.requestedQuantity }}</h1>
          </div>
          <app-status-badge [value]="p.status" />
        </header>

        <section class="panel">
          <h2>Completion</h2>
          <app-progress-bar [percent]="p.completionPercent" [status]="p.status" size="large" />
          <dl class="facts">
            <div><dt>Requested</dt><dd>{{ p.requestedQuantity }}</dd></div>
            <div><dt>Fulfilled</dt><dd>{{ p.fulfilledQuantity }}</dd></div>
            <div><dt>Remaining</dt><dd>{{ p.remainingQuantity }}</dd></div>
            <div><dt>Requested by</dt><dd>{{ p.userEmail }}</dd></div>
            <div><dt>Created</dt><dd>{{ p.createdAt | date: 'medium' }}</dd></div>
            <div><dt>Completed</dt><dd>{{ p.completedAt ? (p.completedAt | date: 'medium') : '—' }}</dd></div>
            <div>
              <dt>Covered by</dt>
              <dd>
                {{ p.allocationCount }} movement(s)
                @if (p.completedBySingleMovement === true) { <span class="muted">· single shipment</span> }
                @if (p.completedBySingleMovement === false) { <span class="muted">· several shipments</span> }
              </dd>
            </div>
          </dl>
        </section>

        <section class="panel">
          <h2>Completion email</h2>
          @if (p.notification; as n) {
            <div class="email-status">
              @switch (n.status) {
                @case ('SENT') { <span class="tag">Sent</span> }
                @case ('PENDING') { <span class="tag queued">{{ n.attempts ? 'Retrying' : 'Queued' }}</span> }
                @case ('FAILED') { <span class="tag failed">Failed</span> }
              }
              <span>to {{ n.recipient }}</span>
            </div>
            <dl class="facts">
              <div><dt>Attempts</dt><dd>{{ n.attempts }}</dd></div>
              @if (n.sentAt) {
                <div><dt>Sent at</dt><dd>{{ n.sentAt | date: 'medium' }}</dd></div>
              } @else if (n.lastAttemptAt) {
                <div><dt>Last attempt</dt><dd>{{ n.lastAttemptAt | date: 'medium' }}</dd></div>
              }
              @if (n.nextAttemptAt) {
                <div><dt>Next attempt</dt><dd>{{ n.nextAttemptAt | date: 'medium' }}</dd></div>
              }
            </dl>
            @if (n.lastError && n.status !== 'SENT') {
              <p class="error">Last error: {{ n.lastError }}</p>
            }
            @if (n.status !== 'SENT') {
              <button type="button" class="secondary retry" (click)="retryEmail(p.orderId)" [disabled]="retrying()">
                {{ retrying() ? 'Sending…' : 'Retry now' }}
              </button>
            }
          } @else {
            <p class="muted">The user is emailed automatically when the order reaches 100%.</p>
          }
        </section>

        <section class="panel">
          <h2>Fulfillment history</h2>
          <p class="muted hint">Every stock allocation that covered this order, in order, with the progress after each one.</p>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Movement</th><th>Date</th><th class="num">Qty used</th><th>Stock from</th>
                  <th class="num">Cumulative</th><th>Progress after</th><th></th>
                </tr>
              </thead>
              <tbody>
                @for (a of p.allocations; track a.movementId) {
                  <tr>
                    <td><a [routerLink]="['/inventory/movements', a.movementId]">OUT #{{ a.movementId }}</a></td>
                    <td class="muted">{{ a.allocatedAt | date: 'short' }}</td>
                    <td class="num">{{ a.quantity }}</td>
                    <td>
                      @if (a.sourceMovementId) {
                        <a [routerLink]="['/inventory/movements', a.sourceMovementId]">IN #{{ a.sourceMovementId }}</a>
                      } @else {
                        <span class="muted">Stock on hand</span>
                      }
                    </td>
                    <td class="num">{{ a.cumulativeFulfilled }} / {{ p.requestedQuantity }}</td>
                    <td><app-progress-bar [percent]="a.cumulativePercent" [status]="a.completesOrder ? 'COMPLETED' : 'PARTIALLY_FULFILLED'" /></td>
                    <td>@if (a.completesOrder) { <span class="tag">Completed the order</span> }</td>
                  </tr>
                } @empty {
                  <tr><td colspan="7" class="empty">No stock allocated yet. This order will be filled as inventory arrives.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      } @else if (notFound()) {
        <p class="empty">Order not found.</p>
      } @else {
        <p class="empty">Loading…</p>
      }
    </section>
  `,
  styles: `
    .hint { margin: -4px 0 4px; font-size: .88rem; }
    .email-status { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .tag.queued { background: var(--warning-soft); color: var(--warning); }
    .tag.failed { background: var(--danger-soft); color: var(--danger); }
    .error { margin: 0; padding: 10px 12px; border-radius: 10px; background: var(--danger-soft); color: var(--danger); font-size: .88rem; overflow-wrap: anywhere; }
    .retry { width: auto; align-self: flex-start; }
  `
})
export class OrderDetailPage {
  private static readonly REFRESH_WHILE_QUEUED_MS = 5000;

  private readonly api = inject(ApiService);
  private readonly toasts = inject(ToastService);

  readonly id = input.required({ transform: numberAttribute });
  readonly progress = signal<OrderProgress | null>(null);
  readonly notFound = signal(false);
  readonly retrying = signal(false);
  private refreshTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.refreshTimer));

    effect(() => {
      const id = this.id();
      this.progress.set(null);
      this.notFound.set(false);
      this.load(id);
    });
  }

  retryEmail(orderId: number): void {
    this.retrying.set(true);
    this.api.retryOrderNotification(orderId).subscribe({
      next: (progress) => {
        this.retrying.set(false);
        this.show(progress);
        if (progress.notification?.status === 'SENT') {
          this.toasts.success(`Email sent to ${progress.notification.recipient}`);
        } else {
          this.toasts.error(`Email could not be sent: ${progress.notification?.lastError ?? 'unknown error'}`);
        }
      },
      error: () => this.retrying.set(false)
    });
  }

  private load(id: number): void {
    this.api.getOrderProgress(id).subscribe({
      next: (progress) => this.show(progress),
      error: () => this.notFound.set(true)
    });
  }

  /**
   * While the email is queued the view keeps refreshing, to reflect the background send or its retries.
   */
  private show(progress: OrderProgress): void {
    this.progress.set(progress);
    clearTimeout(this.refreshTimer);
    if (progress.notification?.status === 'PENDING') {
      this.refreshTimer = setTimeout(() => this.load(progress.orderId), OrderDetailPage.REFRESH_WHILE_QUEUED_MS);
    }
  }
}
