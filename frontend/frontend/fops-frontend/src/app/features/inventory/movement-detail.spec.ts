import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { aMovementDetail, anOrder } from '../../../testing/fixtures';
import { MovementDetailPage } from './movement-detail';

describe('MovementDetailPage', () => {
  let api: ApiMock;

  beforeEach(() => {
    api = createApiMock();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideApiMock(api)] });
  });

  function render(id = 1) {
    const fixture = TestBed.createComponent(MovementDetailPage);
    fixture.componentRef.setInput('id', String(id));
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  it('shows where incoming stock went', () => {
    api.getMovement.mockReturnValue(of(aMovementDetail({ quantity: 12 })));

    const text = render();

    expect(api.getMovement).toHaveBeenCalledWith(1);
    expect(text).toContain('Incoming stock');
    expect(text).toContain('10 of 12 units went to orders waiting on arrival · 2 added to stock on hand.');
    expect(text).toContain('OUT #2');
    expect(text).toContain('Completed the order');
  });

  it('shows the source and order of an allocation', () => {
    api.getMovement.mockReturnValue(of(aMovementDetail({
      id: 2,
      movementType: 'OUT',
      quantity: 8,
      sourceMovementId: 1,
      completesOrder: true,
      order: anOrder({ id: 5, status: 'COMPLETED', fulfilledQuantity: 8, requestedQuantity: 8, completionPercent: 100 }),
      allocations: []
    })));

    const text = render(2);

    expect(text).toContain('Allocation to order');
    expect(text).toContain('IN #1');
    expect(text).toContain('Order #5');
    expect(text).toContain('8 / 8');
    expect(text).not.toContain('Where this stock went');
  });

  it('explains allocations that used stock on hand', () => {
    api.getMovement.mockReturnValue(of(aMovementDetail({ movementType: 'OUT', sourceMovementId: null, order: anOrder(), allocations: [] })));

    expect(render()).toContain('Stock on hand when the order was created');
  });

  it('shows not found for unknown movements', () => {
    api.getMovement.mockReturnValue(throwError(() => new Error('404')));

    expect(render(404)).toContain('Movement not found.');
  });
});
