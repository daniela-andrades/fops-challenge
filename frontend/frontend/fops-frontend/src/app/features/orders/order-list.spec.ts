import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { anItem, anOrder, aUser } from '../../../testing/fixtures';
import { OrderListPage } from './order-list';

describe('OrderListPage', () => {
  let api: ApiMock;

  beforeEach(() => {
    api = createApiMock();
    api.getUsers.mockReturnValue(of([aUser({ id: 1, name: 'Ana' })]));
    api.getItems.mockReturnValue(of([anItem({ id: 1, name: 'Laptop' })]));
    api.getOrders.mockReturnValue(of([
      anOrder({ id: 2, status: 'PARTIALLY_FULFILLED', fulfilledQuantity: 4, remainingQuantity: 6, completionPercent: 40 }),
      anOrder({ id: 1, itemId: 99, status: 'COMPLETED', fulfilledQuantity: 10, remainingQuantity: 0, completionPercent: 100 })
    ]));
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideApiMock(api)] });
  });

  function render() {
    const fixture = TestBed.createComponent(OrderListPage);
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  it('renders one row per order with names, quantities and progress', () => {
    const { host } = render();

    const rows = host.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    const cells = Array.from(rows[0].querySelectorAll('td')).map((td) => td.textContent!.trim());
    expect(cells.slice(0, 7)).toEqual(['#2', 'Ana', 'Laptop', 'Partial', '10', '4', '6']);
    expect(rows[0].querySelector('app-progress-bar')).not.toBeNull();
    expect(host.querySelector('.page-header')!.textContent).toContain('2 orders');
  });

  it('falls back to ids for unknown items', () => {
    expect(render().host.querySelectorAll('tbody tr')[1].textContent).toContain('Item #99');
  });

  it('reloads with the selected filters', () => {
    const { page } = render();

    page.filters = { userId: 1, itemId: null, status: 'COMPLETED' };
    page.load();

    expect(api.getOrders).toHaveBeenLastCalledWith({ userId: 1, itemId: null, status: 'COMPLETED' });
  });

  it('shows an empty state', () => {
    api.getOrders.mockReturnValue(of([]));

    expect(render().host.querySelector('.empty')?.textContent).toContain('No orders match these filters');
  });

  it('keeps cancelled orders visible and lets you filter by them', () => {
    api.getOrders.mockReturnValue(of([anOrder({ id: 5, status: 'CANCELLED', fulfilledQuantity: 2, completionPercent: 20 })]));
    const { host, page } = render();

    expect(host.querySelector('tbody tr')!.textContent).toContain('Cancelled');
    expect(page.statuses.map((s) => s.value)).toContain('CANCELLED');

    page.filters = { userId: null, itemId: null, status: 'CANCELLED' };
    page.load();
    expect(api.getOrders).toHaveBeenLastCalledWith({ userId: null, itemId: null, status: 'CANCELLED' });
  });

  it('shows 10 orders per page by default and pages through the rest', () => {
    api.getOrders.mockReturnValue(of(Array.from({ length: 23 }, (_, i) => anOrder({ id: 23 - i }))));
    const { host, fixture } = render();

    expect(host.querySelectorAll('tbody tr')).toHaveLength(10);
    expect(host.querySelector('.page-header')!.textContent).toContain('23 orders');

    host.querySelector<HTMLButtonElement>('app-paginator .next')!.click();
    fixture.detectChanges();
    host.querySelector<HTMLButtonElement>('app-paginator .next')!.click();
    fixture.detectChanges();

    const ids = Array.from(host.querySelectorAll('tbody tr')).map((r) => r.querySelector('td')!.textContent!.trim());
    expect(ids).toEqual(['#3', '#2', '#1']);
  });

  it('goes back to the first page when the filters reload the list', () => {
    api.getOrders.mockReturnValue(of(Array.from({ length: 30 }, (_, i) => anOrder({ id: i + 1 }))));
    const { page } = render();
    page.page.set(2);

    page.load();

    expect(page.page()).toBe(1);
  });

  it('searches by #order, user name or email, and item name or SKU', () => {
    api.getUsers.mockReturnValue(of([aUser({ id: 1, name: 'Ana', email: 'ana@test.local' }), aUser({ id: 2, name: 'Luis', email: 'luis@test.local' })]));
    api.getItems.mockReturnValue(of([anItem({ id: 1, name: 'Laptop', sku: 'LAP-001' }), anItem({ id: 2, name: 'Mouse', sku: 'MOU-001' })]));
    api.getOrders.mockReturnValue(of([
      anOrder({ id: 12, userId: 1, itemId: 1 }),
      anOrder({ id: 7, userId: 2, itemId: 2 }),
      anOrder({ id: 3, userId: 2, itemId: 1 })
    ]));
    const { page, fixture, host } = render();
    const search = (query: string) => {
      page.search.set(query);
      fixture.detectChanges();
      return Array.from(host.querySelectorAll('tbody tr')).map((r) => r.querySelector('td')!.textContent!.trim());
    };

    expect(search('#7')).toEqual(['#7']);
    expect(search('luis@')).toEqual(['#7', '#3']);
    expect(search('lap-001')).toEqual(['#12', '#3']);
    expect(search('mouse')).toEqual(['#7']);
    expect(search('nothing')).toEqual(['No orders match these filters.']);
    expect(host.querySelector('.page-header')!.textContent).toContain('0 orders');
  });
});
