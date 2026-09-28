import { Component, computed, input } from '@angular/core';
import { MovementType, OrderStatus } from '../../core/models';

const LABELS: Record<OrderStatus | MovementType, string> = {
  PENDING: 'Pending',
  PARTIALLY_FULFILLED: 'Partial',
  COMPLETED: 'Completed',
  IN: 'In',
  OUT: 'Out'
};

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="value()">{{ label() }}</span>`,
  styles: `
    .badge { display: inline-flex; padding: 4px 10px; border-radius: 999px; font-size: .72rem; font-weight: 700; white-space: nowrap; }
    .PENDING { background: var(--warning-soft); color: var(--warning); }
    .PARTIALLY_FULFILLED { background: var(--info-soft); color: var(--info); }
    .COMPLETED, .IN { background: var(--success-soft); color: var(--success); }
    .OUT { background: var(--surface-muted); color: var(--text-muted); }
  `
})
export class StatusBadge {
  readonly value = input.required<OrderStatus | MovementType>();
  protected readonly label = computed(() => LABELS[this.value()] ?? this.value());
}
