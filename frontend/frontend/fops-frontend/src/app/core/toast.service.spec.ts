import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let toasts: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    toasts = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  it('queues toasts with unique ids', () => {
    toasts.success('Saved');
    toasts.error('Failed');

    const [first, second] = toasts.toasts();
    expect(first).toMatchObject({ kind: 'success', message: 'Saved' });
    expect(second).toMatchObject({ kind: 'error', message: 'Failed' });
    expect(first.id).not.toBe(second.id);
  });

  it('auto-dismisses success after 4s and errors after 7s', () => {
    toasts.success('Saved');
    toasts.error('Failed');

    vi.advanceTimersByTime(4000);
    expect(toasts.toasts().map((t) => t.message)).toEqual(['Failed']);

    vi.advanceTimersByTime(3000);
    expect(toasts.toasts()).toEqual([]);
  });

  it('dismisses a toast manually', () => {
    toasts.success('Saved');

    toasts.dismiss(toasts.toasts()[0].id);

    expect(toasts.toasts()).toEqual([]);
  });
});
