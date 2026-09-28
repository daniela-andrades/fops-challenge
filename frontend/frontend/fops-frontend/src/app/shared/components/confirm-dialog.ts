import { Component, inject } from '@angular/core';
import { ConfirmService } from '../../core/confirm.service';

@Component({
  selector: 'app-confirm-dialog',
  host: { '(document:keydown.escape)': 'confirm.answer(false)' },
  template: `
    @if (confirm.pending(); as request) {
      <div class="backdrop" (click)="confirm.answer(false)">
        <div class="dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title"
             aria-describedby="confirm-message" (click)="$event.stopPropagation()">
          <h2 id="confirm-title">{{ request.title }}</h2>
          <p id="confirm-message">{{ request.message }}</p>
          <div class="actions">
            <button type="button" class="secondary" (click)="confirm.answer(false)">Cancel</button>
            <button type="button" class="confirm" [class.danger]="request.danger" (click)="confirm.answer(true)">
              {{ request.confirmLabel ?? 'Confirm' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .backdrop { position: fixed; inset: 0; z-index: 20; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }
    .dialog { width: min(440px, 100%); background: var(--surface); border-radius: 16px; padding: 22px; box-shadow: var(--shadow); }
    h2 { margin: 0 0 8px; }
    p { margin: 0 0 18px; color: var(--text-muted); line-height: 1.45; }
    .actions { display: flex; justify-content: flex-end; gap: 10px; }
    .actions button { width: auto; }
  `
})
export class ConfirmDialog {
  protected readonly confirm = inject(ConfirmService);
}
