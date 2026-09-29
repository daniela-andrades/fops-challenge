import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { defer, of, Subject, throwError } from 'rxjs';
import { Order } from '../../core/models';
import { ToastService } from '../../core/toast.service';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { aMovement, aMovementDetail, anItem, anOrder, aSummary, aUser } from '../../../testing/fixtures';
import { DashboardPage } from './dashboard';

describe('DashboardPage', () => {
  let api: ApiMock;
  let toasts: ToastService;

  beforeEach(() => {
    api = createApiMock();
    api.getUsers.mockReturnValue(of([aUser()]));
    api.getItems.mockReturnValue(of([anItem({ id: 1, name: 'Laptop' }), anItem({ id: 2, name: 'Mouse', sku: 'MOU-1' })]));
    api.getOrders.mockReturnValue(of([
      anOrder({ id: 1, status: 'COMPLETED', completionPercent: 100 }),
      anOrder({ id: 2, itemId: 2, status: 'PARTIALLY_FULFILLED', completionPercent: 40 })
    ]));
    api.getMovements.mockReturnValue(of([aMovement({ id: 1 }), aMovement({ id: 2, movementType: 'OUT', orderId: 1, completesOrder: true })]));

    TestBed.configureTestingModule({ providers: [provideRouter([]), provideApiMock(api)] });
    toasts = TestBed.inject(ToastService);
  });

  function render() {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  it('shows the KPIs from the dashboard summary', () => {
    const { host } = render();

    const cards = Array.from(host.querySelectorAll('.stat-card')).map((card) => ({
      label: card.querySelector('span')!.textContent!.trim(),
      value: card.querySelector('strong')!.textContent!.trim(),
      detail: card.querySelector('small')?.textContent!.replace(/\s+/g, ' ').trim()
    }));
    expect(cards[0]).toEqual({ label: 'Open orders', value: '3', detail: '2 pending · 1 partial' });
    expect(cards[1]).toEqual({ label: 'Completed orders', value: '6', detail: '5 emails sent · 1 queued' });
    expect(cards[2]).toMatchObject({ label: 'Open demand', value: '17' });
    expect(cards[3]).toMatchObject({ label: 'Stock on hand', value: '42', detail: '4 items · 1 out of stock' });
  });

  it('highlights failed emails', () => {
    api.getDashboardSummary.mockReturnValue(of(aSummary({ notificationsFailed: 2 })));

    expect(render().host.querySelector('.failed')?.textContent).toContain('2 failed');
  });

  it('lists only open orders, with item names and progress', () => {
    const { host } = render();

    const rows = host.querySelectorAll('.grid.two section:first-child tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('#2');
    expect(rows[0].textContent).toContain('Mouse');
    expect(rows[0].querySelector('app-progress-bar')).not.toBeNull();
  });

  it('shows the latest movements first and marks the one that completed an order', () => {
    const { host } = render();

    const section = Array.from(host.querySelectorAll('section.panel')).find((s) => s.querySelector('h2')?.textContent === 'Latest movements')!;
    const rows = Array.from(section.querySelectorAll('tbody tr'));
    expect(rows.map((r) => r.querySelector('td')!.textContent!.trim())).toEqual(['#2', '#1']);
    expect(rows[0].textContent).toContain('completed it');
  });

  it('creates an order, reports how much was fulfilled and reloads', () => {
    api.createOrder.mockReturnValue(of(anOrder({ id: 9, status: 'PARTIALLY_FULFILLED', completionPercent: 70 })));
    const { page } = render();
    api.getDashboardSummary.mockClear();

    page.orderForm = { userId: 1, itemId: 2, requestedQuantity: 10 };
    page.createOrder();

    expect(api.createOrder).toHaveBeenCalledWith({ userId: 1, itemId: 2, requestedQuantity: 10 }, expect.any(String));
    expect(toasts.toasts().at(-1)?.message).toBe('Order #9 created · 70% fulfilled (partially fulfilled)');
    expect(page.orderForm.userId).toBeNull();
    expect(api.getDashboardSummary).toHaveBeenCalledTimes(1);
  });

  it('keeps the Idempotency-Key when an order is retried after a failure and renews it after success', () => {
    api.createOrder
      .mockReturnValueOnce(throwError(() => new Error('network')))
      .mockReturnValue(of(anOrder({ id: 9, status: 'COMPLETED', completionPercent: 100 })));
    const { page } = render();

    page.orderForm = { userId: 1, itemId: 2, requestedQuantity: 3 };
    page.createOrder();
    page.createOrder();
    page.orderForm = { userId: 1, itemId: 2, requestedQuantity: 3 };
    page.createOrder();

    const keys = api.createOrder.mock.calls.map((call) => call[1]);
    expect(keys[0]).toEqual(expect.any(String));
    expect(keys[1]).toBe(keys[0]);
    expect(keys[2]).not.toBe(keys[0]);
  });

  it('ignores a second submit while the first order is still in flight', () => {
    const response = new Subject<Order>();
    let requestsSent = 0;
    api.createOrder.mockReturnValue(defer(() => {
      requestsSent++;
      return response;
    }));
    const { page, fixture, host } = render();

    page.orderForm = { userId: 1, itemId: 2, requestedQuantity: 3 };
    page.createOrder();
    page.createOrder();
    fixture.detectChanges();

    expect(requestsSent).toBe(1);
    expect(host.querySelector<HTMLButtonElement>('form:first-child button')!.disabled).toBe(true);

    response.next(anOrder({ id: 4 }));
    response.complete();
    expect(page.busy()).toBe(false);
  });

  it('does not submit an order without user or item', () => {
    const { page, host } = render();

    page.createOrder();

    expect(api.createOrder).not.toHaveBeenCalled();
    expect(host.querySelector<HTMLButtonElement>('form:first-child button')!.disabled).toBe(true);
  });

  it('summarizes where incoming stock was allocated', () => {
    api.registerIncomingInventory.mockReturnValue(of(aMovement({ id: 1 })));
    api.getMovement.mockReturnValue(of(aMovementDetail()));
    const { page } = render();

    page.inventoryForm = { itemId: 1, quantity: 10, reason: '  Supplier A ' };
    page.registerInventory();

    expect(api.registerIncomingInventory).toHaveBeenCalledWith({ itemId: 1, quantity: 10, reason: 'Supplier A' });
    expect(toasts.toasts().at(-1)?.message).toBe('10 units of LAP-001 received · allocated to 2 order(s), 1 completed');
  });

  it('tells when incoming stock found no open orders', () => {
    api.registerIncomingInventory.mockReturnValue(of(aMovement({ id: 1 })));
    api.getMovement.mockReturnValue(of(aMovementDetail({ allocations: [] })));
    const { page } = render();

    page.inventoryForm = { itemId: 1, quantity: 10, reason: '' };
    page.registerInventory();

    expect(toasts.toasts().at(-1)?.message).toContain('no open orders to allocate');
  });

  it('keeps the form and re-enables it when the API rejects the request', () => {
    api.createUser.mockReturnValue(throwError(() => new Error('409')));
    const { page } = render();

    page.userForm = { name: 'Ana', email: 'ana@test.local' };
    page.createUser();

    expect(page.busy()).toBe(false);
    expect(page.userForm.email).toBe('ana@test.local');
  });

  it('creates items and users', () => {
    api.createItem.mockReturnValue(of(anItem({ sku: 'NEW-1', stockOnHand: 5 })));
    api.createUser.mockReturnValue(of(aUser({ name: 'Luis' })));
    const { page } = render();

    page.itemForm = { name: 'New', sku: 'NEW-1', stockOnHand: 5 };
    page.createItem();
    page.userForm = { name: 'Luis', email: 'luis@test.local' };
    page.createUser();

    expect(api.createItem).toHaveBeenCalledWith({ name: 'New', sku: 'NEW-1', stockOnHand: 5 });
    expect(toasts.toasts().map((t) => t.message)).toEqual(['Item NEW-1 created with 5 units', 'User Luis created']);
  });
});
