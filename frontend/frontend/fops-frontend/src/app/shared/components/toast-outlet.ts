import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-toast-outlet',
  template: `
    <div class="stack" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class.error]="toast.kind === 'error'" role="status">
          <span>{{ toast.message }}</span>
          <button type="button" class="close" (click)="toasts.dismiss(toast.id)" aria-label="Dismiss">×</button>
        </div>
      }
    </div>
  `,
  styles: `
    .stack { position: fixed; right: 16px; bottom: 16px; display: flex; flex-direction: column; gap: 8px; z-index: 10; max-width: min(420px, calc(100vw - 32px)); }
    .toast { display: flex; gap: 12px; align-items: flex-start; justify-content: space-between; padding: 12px 14px; border-radius: 12px;
             background: var(--success-soft); color: var(--success); border: 1px solid currentColor; box-shadow: var(--shadow); font-size: .9rem; }
    .toast.error { background: var(--danger-soft); color: var(--danger); }
    .close { background: none; border: none; color: inherit; font-size: 1.1rem; line-height: 1; cursor: pointer; padding: 0; width: auto; }
  `
})
export class ToastOutlet {
  protected readonly toasts = inject(ToastService);
}
