import { TestBed } from '@angular/core/testing';
import { ToastService } from '../../core/toast.service';
import { ToastOutlet } from './toast-outlet';

describe('ToastOutlet', () => {
  it('renders queued toasts and dismisses them on click', () => {
    const toasts = TestBed.inject(ToastService);
    const fixture = TestBed.createComponent(ToastOutlet);
    const host = fixture.nativeElement as HTMLElement;

    toasts.success('Order #1 created');
    toasts.error('Out of stock');
    fixture.detectChanges();

    const items = host.querySelectorAll('.toast');
    expect(items).toHaveLength(2);
    expect(items[1].classList).toContain('error');
    expect(items[1].textContent).toContain('Out of stock');

    host.querySelector<HTMLButtonElement>('.toast .close')!.click();
    fixture.detectChanges();

    expect(host.querySelectorAll('.toast')).toHaveLength(1);
  });
});
