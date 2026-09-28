import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { OrderStatus } from '../../core/models';

@Component({
  selector: 'app-progress-bar',
  imports: [DecimalPipe],
  template: `
    <div class="bar" [class.large]="size() === 'large'" role="progressbar"
         [attr.aria-valuenow]="clamped()" aria-valuemin="0" aria-valuemax="100">
      <div class="fill" [class]="status()" [style.width.%]="clamped()"></div>
    </div>
    <span class="label">{{ clamped() | number: '1.0-1' }}%</span>
  `,
  styles: `
    :host { display: flex; align-items: center; gap: 10px; min-width: 120px; }
    .bar { flex: 1; height: 8px; background: var(--surface-muted); border-radius: 999px; overflow: hidden; }
    .bar.large { height: 14px; }
    .fill { height: 100%; border-radius: inherit; background: var(--accent); transition: width .4s ease; }
    .fill.PARTIALLY_FULFILLED { background: var(--info); }
    .fill.COMPLETED { background: var(--success); }
    .label { font-variant-numeric: tabular-nums; font-size: .82rem; color: var(--text-muted); min-width: 3.5em; text-align: right; }
  `
})
export class ProgressBar {
  readonly percent = input.required<number>();
  readonly status = input<OrderStatus>('PENDING');
  readonly size = input<'normal' | 'large'>('normal');

  protected readonly clamped = computed(() => Math.min(100, Math.max(0, this.percent() ?? 0)));
}
