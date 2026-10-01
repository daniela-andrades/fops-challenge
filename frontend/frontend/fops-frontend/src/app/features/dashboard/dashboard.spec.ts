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

  it('keeps cancelled orders out of the open orders panel', () => {
    api.getOrders.mockReturnValue(of([
      anOrder({ id: 1, status: 'CANCELLED', completionPercent: 40 }),
      anOrder({ id: 2, status: 'PENDING' })
    ]));

    const { host } = render();

    const rows = host.querySelectorAll('.grid.two section:first-child tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('#2');
  });

  describe('create item and create user buttons', () => {
    const button = (host: HTMLElement, label: string) =>
      Array.from(host.querySelectorAll<HTMLButtonElement>('button')).find((b) => b.textContent!.trim() === label)!;

    async function typeInto(fixture: ReturnType<typeof render>['fixture'], host: HTMLElement, values: Record<string, string>) {
      for (const [name, value] of Object.entries(values)) {
        const input = host.querySelector<HTMLInputElement>(`input[name=${name}]`)!;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      }
      fixture.detectChanges();
      await fixture.whenStable();
    }

    it('enables Save item only with a name, a SKU and a non-negative initial stock', async () => {
      const { fixture, host } = render();
      await fixture.whenStable();
      const disabled = () => button(host, 'Save item').disabled;

      expect(disabled()).toBe(true);
      await typeInto(fixture, host, { itemName: 'Cable', itemSku: '  ' });
      expect(disabled()).toBe(true);
      await typeInto(fixture, host, { itemSku: 'CAB-1', stockOnHand: '-1' });
      expect(disabled()).toBe(true);
      await typeInto(fixture, host, { stockOnHand: '0' });
      expect(disabled()).toBe(false);
      await typeInto(fixture, host, { stockOnHand: '' });
      expect(disabled()).toBe(false);
    });

    it('enables Save user only with a name and a valid email', async () => {
      const { fixture, host } = render();
      await fixture.whenStable();
      const disabled = () => button(host, 'Save user').disabled;

      expect(disabled()).toBe(true);
      await typeInto(fixture, host, { userName: 'Ana', userEmail: 'ana@' });
      expect(disabled()).toBe(true);
      await typeInto(fixture, host, { userName: ' ', userEmail: 'ana@fops.local' });
      expect(disabled()).toBe(true);
      await typeInto(fixture, host, { userName: 'Ana' });
      expect(disabled()).toBe(false);
    });

    it('does not send an incomplete item or user even if submitted', () => {
      const { page } = render();

      page.itemForm = { name: '', sku: 'CAB-1', stockOnHand: 0 };
      page.createItem();
      page.userForm = { name: 'Ana', email: 'not-an-email' };
      page.createUser();

      expect(api.createItem).not.toHaveBeenCalled();
      expect(api.createUser).not.toHaveBeenCalled();
    });
  });

  describe('current inventory with outstanding demand', () => {
    function inventoryRows(host: HTMLElement) {
      const card = Array.from(host.querySelectorAll('section.panel')).find((s) => s.querySelector('h2')?.textContent === 'Current inventory')!;
      return Array.from(card.querySelectorAll('tbody tr')).map((row) => ({
        sku: row.children[1].textContent!.trim(),
        demand: row.querySelector('td.demand')!.childNodes[0].textContent!.trim(),
        shortfall: row.querySelector('.shortfall')?.textContent!.trim() ?? null,
        marked: row.classList.contains('short')
      }));
    }

    it('shows 0 demand for items without open orders, never blank', () => {
      api.getItems.mockReturnValue(of([
        anItem({ id: 1, sku: 'A', stockOnHand: 5, outstandingDemand: 0 }),
        anItem({ id: 2, sku: 'B', stockOnHand: 5 })
      ]));

      const rows = inventoryRows(render().host);

      expect(rows.map((r) => r.demand)).toEqual(['0', '0']);
      expect(rows.every((r) => !r.marked && r.shortfall === null)).toBe(true);
    });

    it('marks items whose demand exceeds stock and shows the units to reorder', () => {
      api.getItems.mockReturnValue(of([
        anItem({ id: 1, sku: 'COVERED', stockOnHand: 10, outstandingDemand: 4 }),
        anItem({ id: 2, sku: 'SHORT', stockOnHand: 2, outstandingDemand: 9 })
      ]));

      const short = inventoryRows(render().host).find((r) => r.sku === 'SHORT')!;
      const covered = inventoryRows(render().host).find((r) => r.sku === 'COVERED')!;

      expect(short).toEqual({ sku: 'SHORT', demand: '9', shortfall: 'short 7', marked: true });
      expect(covered).toEqual({ sku: 'COVERED', demand: '4', shortfall: null, marked: false });
    });

    it('lists the biggest shortfall first and keeps the order of the rest', () => {
      api.getItems.mockReturnValue(of([
        anItem({ id: 1, sku: 'FIRST', stockOnHand: 3, outstandingDemand: 0 }),
        anItem({ id: 2, sku: 'SMALL-GAP', stockOnHand: 0, outstandingDemand: 2 }),
        anItem({ id: 3, sku: 'SECOND', stockOnHand: 8, outstandingDemand: 8 }),
        anItem({ id: 4, sku: 'BIG-GAP', stockOnHand: 1, outstandingDemand: 11 }),
        anItem({ id: 5, sku: 'THIRD', stockOnHand: 0, outstandingDemand: 0 })
      ]));

      expect(inventoryRows(render().host).map((r) => r.sku)).toEqual(['BIG-GAP', 'SMALL-GAP', 'FIRST', 'SECOND', 'THIRD']);
    });
  });

  it('shows the current inventory 10 rows at a time, shortages first', () => {
    api.getItems.mockReturnValue(of(Array.from({ length: 13 }, (_, i) =>
      anItem({ id: i + 1, sku: `SKU-${i + 1}`, stockOnHand: 0, outstandingDemand: i === 12 ? 5 : 0 }))));
    const { host, fixture } = render();
    const card = () => Array.from(host.querySelectorAll('section.panel')).find((s) => s.querySelector('h2')?.textContent === 'Current inventory')!;

    expect(card().querySelectorAll('tbody tr')).toHaveLength(10);
    expect(card().querySelector('tbody tr td:nth-child(2)')!.textContent).toBe('SKU-13');
    expect(card().querySelector('app-paginator .range')!.textContent).toBe('1–10 of 13');

    card().querySelector<HTMLButtonElement>('app-paginator .next')!.click();
    fixture.detectChanges();
    expect(card().querySelectorAll('tbody tr')).toHaveLength(3);
  });
});
