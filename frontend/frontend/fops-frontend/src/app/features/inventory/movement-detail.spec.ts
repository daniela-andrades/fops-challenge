import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { ConfirmService } from '../../core/confirm.service';
import { ToastService } from '../../core/toast.service';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { aMovement } from '../../../testing/fixtures';
import { aMovementDetail, anOrder } from '../../../testing/fixtures';
import { MovementDetailPage } from './movement-detail';

describe('MovementDetailPage', () => {
  let api: ApiMock;

  beforeEach(() => {
    api = createApiMock();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideApiMock(api)] });
  });

  function mount(id = 1) {
    const fixture = TestBed.createComponent(MovementDetailPage);
    fixture.componentRef.setInput('id', String(id));
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  function render(id = 1) {
    return mount(id).host.textContent!.replace(/\s+/g, ' ');
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

  describe('corrections', () => {
    it('edits the reason and reloads the movement', () => {
      api.updateMovement.mockReturnValue(of(aMovement({ reason: 'Supplier B' })));
      const toasts = TestBed.inject(ToastService);
      const { host, fixture, page } = mount();

      host.querySelector<HTMLButtonElement>('.edit-reason')!.click();
      fixture.detectChanges();
      page.reasonDraft = 'Supplier B';
      page.saveReason(1);

      expect(api.updateMovement).toHaveBeenCalledWith(1, { reason: 'Supplier B' });
      expect(api.getMovement).toHaveBeenCalledTimes(2);
      expect(page.editingReason()).toBe(false);
      expect(toasts.toasts().at(-1)?.message).toBe('Reason updated');
    });

    it('offers deletion only for incoming movements whose stock was not allocated', () => {
      api.getMovement.mockReturnValue(of(aMovementDetail()));
      expect(mount().host.querySelector('button.delete')).toBeNull();

      api.getMovement.mockReturnValue(of(aMovementDetail({ id: 2, movementType: 'OUT', order: anOrder(), allocations: [] })));
      const out = mount(2).host;
      expect(out.querySelector('button.delete')).toBeNull();
      expect(out.textContent).toContain('only change through it');
    });

    it('deletes an unallocated incoming movement after confirmation and goes back to the list', async () => {
      api.getMovement.mockReturnValue(of(aMovementDetail({ allocations: [] })));
      const confirm = TestBed.inject(ConfirmService);
      const router = TestBed.inject(Router);
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      const { host } = mount();

      host.querySelector<HTMLButtonElement>('button.delete')!.click();
      expect(confirm.pending()?.message).toContain('10 units of LAP-001 will be removed from stock');
      confirm.answer(true);
      await Promise.resolve();

      expect(api.deleteMovement).toHaveBeenCalledWith(1);
      expect(navigate).toHaveBeenCalledWith(['/inventory']);
    });

    it('does nothing when the deletion is cancelled', async () => {
      api.getMovement.mockReturnValue(of(aMovementDetail({ allocations: [] })));
      const confirm = TestBed.inject(ConfirmService);
      const { page } = mount();

      const deletion = page.deleteMovement(aMovementDetail({ allocations: [] }));
      confirm.answer(false);
      await deletion;

      expect(api.deleteMovement).not.toHaveBeenCalled();
    });
  });
});
