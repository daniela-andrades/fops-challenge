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
});
