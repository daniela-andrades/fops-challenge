import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../core/toast.service';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { aNotification, anOrder, aProgress } from '../../../testing/fixtures';
import { ConfirmService } from '../../core/confirm.service';
import { OrderDetailPage } from './order-detail';

describe('OrderDetailPage', () => {
  let api: ApiMock;

  beforeEach(() => {
    api = createApiMock();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideApiMock(api)] });
  });

  afterEach(() => vi.useRealTimers());

  function render(id = 2) {
    const fixture = TestBed.createComponent(OrderDetailPage);
    fixture.componentRef.setInput('id', String(id));
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim() ?? '';

  it('loads the order from the route id', () => {
    const { host } = render(2);

    expect(api.getOrderProgress).toHaveBeenCalledWith(2);
    expect(text(host.querySelector('h1'))).toBe('LAP-001 × 5');
    expect(text(host.querySelector('.facts'))).toContain('2 movement(s) · several shipments');
  });

  it('lists every allocation with its source, cumulative progress and the one that completed the order', () => {
    const { host } = render();

    const rows = host.querySelectorAll('section.panel:last-child tbody tr');
    expect(rows).toHaveLength(2);
    expect(text(rows[0])).toContain('OUT #3');
    expect(text(rows[0])).toContain('IN #1');
    expect(text(rows[0])).toContain('2 / 5');
    expect(text(rows[1])).toContain('Completed the order');
  });

  it('marks allocations taken from stock on hand', () => {
    api.getOrderProgress.mockReturnValue(of(aProgress({
      allocationCount: 1,
      completedBySingleMovement: true,
      allocations: [{ movementId: 3, sourceMovementId: null, quantity: 5, allocatedAt: '2026-09-26T10:00:00', cumulativeFulfilled: 5, cumulativePercent: 100, completesOrder: true }]
    })));

    const { host } = render();

    expect(text(host.querySelector('section.panel:last-child tbody'))).toContain('Stock on hand');
    expect(text(host.querySelector('.facts'))).toContain('single shipment');
  });

  it('explains the email is sent at 100% while the order is open', () => {
    api.getOrderProgress.mockReturnValue(of(aProgress({ status: 'PENDING', notification: null, allocations: [] })));

    const { host } = render();

    expect(text(host)).toContain('emailed automatically when the order reaches 100%');
    expect(text(host)).toContain('No stock allocated yet');
  });

  it('shows a sent email without a retry button', () => {
    const { host } = render();

    expect(text(host.querySelector('.email-status .tag'))).toBe('Sent');
    expect(text(host.querySelector('.email-status'))).toContain('to ana@test.local');
    expect(host.querySelector('button.retry')).toBeNull();
  });

  it('shows a failed email with its error and retries it', () => {
    api.getOrderProgress.mockReturnValue(of(aProgress({
      notification: aNotification({ status: 'FAILED', attempts: 5, sentAt: null, lastError: 'Connection refused' })
    })));
    api.retryOrderNotification.mockReturnValue(of(aProgress()));
    const toasts = TestBed.inject(ToastService);
    const { host, fixture } = render();

    expect(text(host.querySelector('.email-status'))).toContain('Failed');
    expect(text(host.querySelector('.error'))).toBe('Last error: Connection refused');

    host.querySelector<HTMLButtonElement>('button.retry')!.click();
    fixture.detectChanges();

    expect(api.retryOrderNotification).toHaveBeenCalledWith(2);
    expect(toasts.toasts().at(-1)).toMatchObject({ kind: 'success', message: 'Email sent to ana@test.local' });
    expect(host.querySelector('button.retry')).toBeNull();
  });

  it('reports a retry that failed again', () => {
    const failed = aProgress({ notification: aNotification({ status: 'FAILED', sentAt: null, lastError: 'Still down' }) });
    api.getOrderProgress.mockReturnValue(of(failed));
    api.retryOrderNotification.mockReturnValue(of(failed));
    const toasts = TestBed.inject(ToastService);
    const { page } = render();

    page.retryEmail(2);

    expect(toasts.toasts().at(-1)).toMatchObject({ kind: 'error', message: 'Email could not be sent: Still down' });
    expect(page.retrying()).toBe(false);
  });

  it('keeps refreshing while the email is queued and stops once sent', () => {
    vi.useFakeTimers();
    api.getOrderProgress
      .mockReturnValueOnce(of(aProgress({ notification: aNotification({ status: 'PENDING', attempts: 0, sentAt: null }) })))
      .mockReturnValue(of(aProgress()));
    render();

    expect(api.getOrderProgress).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(5000);
    expect(api.getOrderProgress).toHaveBeenCalledTimes(2);
    vi.advanceTimersByTime(20000);
    expect(api.getOrderProgress).toHaveBeenCalledTimes(2);
  });

  it('shows retry progress for a queued email', () => {
    api.getOrderProgress.mockReturnValue(of(aProgress({
      notification: aNotification({ status: 'PENDING', attempts: 2, sentAt: null, nextAttemptAt: '2026-09-26T10:10:00', lastError: 'Timeout' })
    })));

    const { host } = render();

    expect(text(host.querySelector('.email-status'))).toContain('Retrying');
    expect(text(host)).toContain('Next attempt');
  });

  it('shows not found when the order does not exist', () => {
    api.getOrderProgress.mockReturnValue(throwError(() => new Error('404')));

    expect(text(render(99).host)).toContain('Order not found.');
  });

  describe('cancellation', () => {
    it('offers cancellation only for pending and partial orders', () => {
      const button = (status: 'PENDING' | 'PARTIALLY_FULFILLED' | 'COMPLETED' | 'CANCELLED') => {
        api.getOrderProgress.mockReturnValue(of(aProgress({ status, notification: null })));
        return render().host.querySelector('button.cancel-order');
      };

      expect(button('PENDING')).not.toBeNull();
      expect(button('PARTIALLY_FULFILLED')).not.toBeNull();
      expect(button('COMPLETED')).toBeNull();
      expect(button('CANCELLED')).toBeNull();
    });

    it('states how many units go back to stock and cancels after confirmation', async () => {
      api.getOrderProgress.mockReturnValue(of(aProgress({ status: 'PARTIALLY_FULFILLED', fulfilledQuantity: 3, notification: null })));
      api.cancelOrder.mockReturnValue(of(anOrder({ id: 2, status: 'CANCELLED' })));
      const confirm = TestBed.inject(ConfirmService);
      const toasts = TestBed.inject(ToastService);
      const { host } = render();

      host.querySelector<HTMLButtonElement>('button.cancel-order')!.click();
      expect(confirm.pending()?.message)
        .toBe('Cancelling will return 3 units to stock. They may be automatically allocated to other pending orders.');
      confirm.answer(true);
      await Promise.resolve();

      expect(api.cancelOrder).toHaveBeenCalledWith(2);
      expect(api.getOrderProgress).toHaveBeenCalledTimes(2);
      expect(toasts.toasts().at(-1)?.message).toBe('Order #2 cancelled · 3 units returned to stock');
    });

    it('says no stock is returned when nothing was allocated', async () => {
      api.getOrderProgress.mockReturnValue(of(aProgress({ status: 'PENDING', fulfilledQuantity: 0, notification: null })));
      api.cancelOrder.mockReturnValue(of(anOrder({ id: 2, status: 'CANCELLED' })));
      const confirm = TestBed.inject(ConfirmService);
      const { page } = render();

      const cancellation = page.cancelOrder(aProgress({ status: 'PENDING', fulfilledQuantity: 0 }));
      expect(confirm.pending()?.message).toBe('Nothing has been allocated to this order yet, so no stock is returned.');
      confirm.answer(true);
      await cancellation;

      expect(api.cancelOrder).toHaveBeenCalledWith(2);
    });

    it('does nothing when the confirmation is dismissed', async () => {
      api.getOrderProgress.mockReturnValue(of(aProgress({ status: 'PENDING', fulfilledQuantity: 0, notification: null })));
      const confirm = TestBed.inject(ConfirmService);
      const { page } = render();

      const cancellation = page.cancelOrder(aProgress({ status: 'PENDING', fulfilledQuantity: 0 }));
      confirm.answer(false);
      await cancellation;

      expect(api.cancelOrder).not.toHaveBeenCalled();
    });

    it('explains a cancelled order and that it is not notified', () => {
      api.getOrderProgress.mockReturnValue(of(aProgress({ status: 'CANCELLED', fulfilledQuantity: 5, notification: null })));

      const { host } = render();

      expect(text(host.querySelector('.cancelled-note'))).toContain('The 5 units allocated before the cancellation were returned to stock');
      expect(text(host)).toContain('Cancelled orders are not notified.');
      expect(text(host.querySelector('app-status-badge'))).toBe('Cancelled');
    });
  });
});
