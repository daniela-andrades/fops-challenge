import { TestBed } from '@angular/core/testing';
import { ConfirmService } from '../../core/confirm.service';
import { ConfirmDialog } from './confirm-dialog';

describe('ConfirmDialog', () => {
  function setup() {
    const service = TestBed.inject(ConfirmService);
    const fixture = TestBed.createComponent(ConfirmDialog);
    const host = fixture.nativeElement as HTMLElement;
    return { service, fixture, host };
  }

  it('renders nothing until a confirmation is requested', () => {
    const { fixture, host } = setup();
    fixture.detectChanges();

    expect(host.querySelector('[role=alertdialog]')).toBeNull();
  });

  it('shows the question and resolves true on confirm', async () => {
    const { service, fixture, host } = setup();
    const answer = service.confirm({ title: 'Delete Ana?', message: 'Cannot be undone', confirmLabel: 'Delete user', danger: true });
    fixture.detectChanges();

    expect(host.querySelector('h2')!.textContent).toBe('Delete Ana?');
    const confirmButton = host.querySelector<HTMLButtonElement>('button.confirm')!;
    expect(confirmButton.textContent!.trim()).toBe('Delete user');
    expect(confirmButton.classList).toContain('danger');

    confirmButton.click();
    await expect(answer).resolves.toBe(true);
  });

  it('resolves false on cancel, backdrop click or Escape', async () => {
    const { service, fixture, host } = setup();

    let answer = service.confirm({ title: 'A', message: '' });
    fixture.detectChanges();
    host.querySelector<HTMLButtonElement>('button.secondary')!.click();
    await expect(answer).resolves.toBe(false);

    answer = service.confirm({ title: 'B', message: '' });
    fixture.detectChanges();
    host.querySelector<HTMLElement>('.backdrop')!.click();
    await expect(answer).resolves.toBe(false);

    answer = service.confirm({ title: 'C', message: '' });
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await expect(answer).resolves.toBe(false);
  });

  it('does not close when clicking inside the dialog', () => {
    const { service, fixture, host } = setup();
    service.confirm({ title: 'Stay', message: 'open' });
    fixture.detectChanges();

    host.querySelector<HTMLElement>('[role=alertdialog]')!.click();

    expect(service.pending()).not.toBeNull();
  });
});
