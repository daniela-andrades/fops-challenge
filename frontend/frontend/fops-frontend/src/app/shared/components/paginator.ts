import { Component, computed, input, model } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PAGE_SIZES, pageCount } from '../pagination';

/**
 * Client-side pager for long tables: range, previous/next and rows per page.
 * Bind with [(page)] and [(pageSize)]; the parent slices its rows with pageOf().
 */
@Component({
  selector: 'app-paginator',
  imports: [DecimalPipe],
  template: `
    @if (total() > 0) {
      <nav class="paginator" aria-label="Pagination">
        <span class="range">{{ first() | number }}–{{ last() | number }} of {{ total() | number }}</span>
        <label class="size">
          Rows
          <select [value]="pageSize()" (change)="changeSize($any($event.target).value)" aria-label="Rows per page">
            @for (size of sizes; track size) {
              <option [value]="size" [selected]="size === pageSize()">{{ size }}</option>
            }
          </select>
        </label>
        <span class="controls">
          <button type="button" class="inline secondary prev" (click)="go(current() - 1)" [disabled]="current() <= 1" aria-label="Previous page">‹ Prev</button>
          <span class="position">Page {{ current() }} of {{ pages() }}</span>
          <button type="button" class="inline secondary next" (click)="go(current() + 1)" [disabled]="current() >= pages()" aria-label="Next page">Next ›</button>
        </span>
      </nav>
    }
  `,
  styles: `
    .paginator { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px 16px; padding-top: 6px; font-size: .88rem; color: var(--text-muted); }
    .size { display: flex; align-items: center; gap: 8px; }
    .size select { width: auto; padding: 6px 10px; }
    .controls { display: flex; align-items: center; gap: 10px; }
    .position { font-variant-numeric: tabular-nums; white-space: nowrap; }
  `
})
export class Paginator {
  readonly total = input.required<number>();
  readonly page = model(1);
  readonly pageSize = model(25);
  protected readonly sizes = PAGE_SIZES;

  protected readonly pages = computed(() => pageCount(this.total(), this.pageSize()));
  protected readonly current = computed(() => Math.min(Math.max(1, this.page()), this.pages()));
  protected readonly first = computed(() => (this.current() - 1) * this.pageSize() + 1);
  protected readonly last = computed(() => Math.min(this.current() * this.pageSize(), this.total()));

  go(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.pages()));
  }

  changeSize(value: string): void {
    this.pageSize.set(Number(value));
    this.page.set(1);
  }
}
