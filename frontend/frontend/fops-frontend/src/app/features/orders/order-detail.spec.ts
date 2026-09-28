import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ToastService } from '../../core/toast.service';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { aNotification, aProgress } from '../../../testing/fixtures';
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
});
