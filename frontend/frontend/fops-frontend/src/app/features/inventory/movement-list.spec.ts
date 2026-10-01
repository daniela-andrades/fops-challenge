import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { aMovement, anItem } from '../../../testing/fixtures';
import { MovementListPage } from './movement-list';

describe('MovementListPage', () => {
  let api: ApiMock;

  beforeEach(() => {
    api = createApiMock();
    api.getItems.mockReturnValue(of([anItem({ id: 1, name: 'Laptop' })]));
    api.getMovements.mockReturnValue(of([
      aMovement({ id: 1, movementType: 'IN', quantity: 10 }),
      aMovement({ id: 2, movementType: 'OUT', orderId: 5, sourceMovementId: 1, quantity: 8, completesOrder: true })
    ]));
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideApiMock(api)] });
  });

  function render() {
    const fixture = TestBed.createComponent(MovementListPage);
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  const firstCells = (host: HTMLElement) =>
    Array.from(host.querySelectorAll('tbody tr')).map((r) => r.querySelector('td')!.textContent!.trim());

  it('lists movements newest first with their traceability links', () => {
    const { host } = render();

    expect(firstCells(host)).toEqual(['#2', '#1']);
    const out = host.querySelector('tbody tr')!;
    expect(out.textContent).toContain('#5');
    expect(out.textContent).toContain('completed it');
    expect(out.textContent).toContain('IN #1');
    expect(out.querySelector('a[href="/orders/5"]')).not.toBeNull();
  });

  it('filters by movement type on the client', () => {
    const { page, host, fixture } = render();

    page.type.set('IN');
    fixture.detectChanges();

    expect(firstCells(host)).toEqual(['#1']);
  });

  it('asks the API for a single item when filtered', () => {
    const { page } = render();

    page.itemId = 1;
    page.load();

    expect(api.getMovements).toHaveBeenLastCalledWith(1);
  });

  describe('search and pagination', () => {
    beforeEach(() => {
      api.getItems.mockReturnValue(of([anItem({ id: 1, name: 'Laptop', sku: 'LAP-001' }), anItem({ id: 2, name: 'Mouse', sku: 'MOU-001' })]));
      api.getMovements.mockReturnValue(of([
        aMovement({ id: 1, itemId: 1, reason: 'Supplier A' }),
        aMovement({ id: 2, itemId: 2, reason: 'Initial stock' }),
        aMovement({ id: 3, itemId: 1, movementType: 'OUT', orderId: 77, reason: 'Allocated' })
      ]));
    });

    it('searches by item name, SKU, reason, #movement and #order', () => {
      const { page, fixture, host } = render();
      const search = (query: string) => {
        page.search.set(query);
        fixture.detectChanges();
        return firstCells(host);
      };

      expect(search('mou-001')).toEqual(['#2']);
      expect(search('laptop')).toEqual(['#3', '#1']);
      expect(search('supplier')).toEqual(['#1']);
      expect(search('#77')).toEqual(['#3']);
      expect(search('nothing like this')).toEqual(['No movements recorded.']);
      expect(host.querySelector('.page-header')!.textContent).toContain('0 movements');
    });

    it('paginates long histories', () => {
      api.getMovements.mockReturnValue(of(Array.from({ length: 60 }, (_, i) => aMovement({ id: i + 1 }))));
      const { host, fixture, page } = render();

      expect(host.querySelectorAll('tbody tr')).toHaveLength(25);
      page.page.set(3);
      fixture.detectChanges();
      expect(firstCells(host)).toEqual(Array.from({ length: 10 }, (_, i) => `#${10 - i}`));
    });
  });
});
