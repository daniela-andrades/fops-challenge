import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ConfirmService } from '../../core/confirm.service';
import { ToastService } from '../../core/toast.service';
import { ApiMock, createApiMock, provideApiMock } from '../../../testing/api-mock';
import { anItem, aUser } from '../../../testing/fixtures';
import { CatalogPage } from './catalog';

describe('CatalogPage', () => {
  let api: ApiMock;
  let confirm: ConfirmService;
  let toasts: ToastService;

  beforeEach(() => {
    api = createApiMock();
    api.getUsers.mockReturnValue(of([aUser({ id: 1, name: 'Ana' }), aUser({ id: 2, name: 'Luis', email: 'luis@test.local' })]));
    api.getItems.mockReturnValue(of([anItem({ id: 7, name: 'Laptop', sku: 'LAP-001', stockOnHand: 12 })]));
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideApiMock(api)] });
    confirm = TestBed.inject(ConfirmService);
    toasts = TestBed.inject(ToastService);
  });

  function render() {
    const fixture = TestBed.createComponent(CatalogPage);
    fixture.detectChanges();
    return { fixture, page: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  const click = (root: Element, text: string) =>
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent!.trim() === text)!.click();

  it('lists users and items with their stock', () => {
    const { host } = render();

    expect(host.querySelectorAll('.users tbody tr')).toHaveLength(2);
    const itemRow = host.querySelector('.items tbody tr')!;
    expect(itemRow.textContent).toContain('LAP-001');
    expect(itemRow.querySelector('.num')!.textContent!.trim()).toBe('12');
  });

  it('edits a user inline and saves it', () => {
    api.updateUser.mockReturnValue(of(aUser({ id: 1, name: 'Ana Maria' })));
    const { host, fixture, page } = render();

    click(host.querySelector('.users tbody tr')!, 'Edit');
    fixture.detectChanges();
    expect(host.querySelector('.users tr.editing')).not.toBeNull();

    page.userDraft = { name: 'Ana Maria', email: 'ana@test.local' };
    click(host.querySelector('.users tr.editing')!, 'Save');

    expect(api.updateUser).toHaveBeenCalledWith(1, { name: 'Ana Maria', email: 'ana@test.local' });
    expect(page.editingUserId()).toBeNull();
    expect(toasts.toasts().at(-1)?.message).toBe('User Ana Maria updated');
  });

  it('keeps the row in edit mode when the update is rejected', () => {
    api.updateUser.mockReturnValue(throwError(() => new Error('409')));
    const { host, fixture, page } = render();

    click(host.querySelector('.users tbody tr')!, 'Edit');
    fixture.detectChanges();
    click(host.querySelector('.users tr.editing')!, 'Save');

    expect(page.editingUserId()).toBe(1);
    expect(page.busy()).toBe(false);
  });

  it('edits an item name and SKU only', () => {
    api.updateItem.mockReturnValue(of(anItem({ id: 7, sku: 'LAP-002' })));
    const { host, fixture, page } = render();

    click(host.querySelector('.items tbody tr')!, 'Edit');
    fixture.detectChanges();
    expect(host.querySelectorAll('.items tr.editing input')).toHaveLength(2);

    page.itemDraft = { name: 'Laptop Pro', sku: 'LAP-002' };
    page.saveItem(7);

    expect(api.updateItem).toHaveBeenCalledWith(7, { name: 'Laptop Pro', sku: 'LAP-002' });
    expect(toasts.toasts().at(-1)?.message).toBe('Item LAP-002 updated');
  });

  it('deletes a user only after confirmation', async () => {
    const { page } = render();

    const cancelled = page.deleteUser(aUser({ id: 2, name: 'Luis' }));
    expect(confirm.pending()?.title).toBe('Delete Luis?');
    confirm.answer(false);
    await cancelled;
    expect(api.deleteUser).not.toHaveBeenCalled();

    const accepted = page.deleteUser(aUser({ id: 2, name: 'Luis' }));
    confirm.answer(true);
    await accepted;
    expect(api.deleteUser).toHaveBeenCalledWith(2);
    expect(toasts.toasts().at(-1)?.message).toBe('User Luis deleted');
  });

  it('deletes an item after confirmation and reloads', async () => {
    const { page } = render();
    api.getItems.mockClear();

    const deletion = page.deleteItem(anItem({ id: 7, name: 'Laptop', sku: 'LAP-001' }));
    expect(confirm.pending()?.danger).toBe(true);
    confirm.answer(true);
    await deletion;

    expect(api.deleteItem).toHaveBeenCalledWith(7);
    expect(api.getItems).toHaveBeenCalledTimes(1);
  });

  describe('search and pagination', () => {
    it('filters users by name or email and items by name or SKU', () => {
      const { page, fixture, host } = render();

      page.userSearch.set('LUIS@');
      page.itemSearch.set('lap-0');
      fixture.detectChanges();

      expect(host.querySelectorAll('.users tbody tr')).toHaveLength(1);
      expect(host.querySelector('.users tbody tr')!.textContent).toContain('Luis');
      expect(host.querySelector('.items tbody tr')!.textContent).toContain('LAP-001');

      page.itemSearch.set('zzz');
      fixture.detectChanges();
      expect(host.querySelector('.items tbody tr')!.textContent).toContain('No items match this search.');
    });

    it('paginates users and items independently', () => {
      api.getUsers.mockReturnValue(of(Array.from({ length: 30 }, (_, i) => aUser({ id: i + 1, name: `User ${i + 1}` }))));
      api.getItems.mockReturnValue(of(Array.from({ length: 12 }, (_, i) => anItem({ id: i + 1, sku: `SKU-${i + 1}` }))));
      const { page, fixture, host } = render();

      expect(host.querySelectorAll('.users tbody tr')).toHaveLength(25);
      expect(host.querySelectorAll('.items tbody tr')).toHaveLength(12);

      page.userPage.set(2);
      fixture.detectChanges();
      expect(host.querySelectorAll('.users tbody tr')).toHaveLength(5);
      expect(host.querySelectorAll('.items tbody tr')).toHaveLength(12);
    });
  });
});
