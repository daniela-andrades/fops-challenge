import { TestBed } from '@angular/core/testing';
import { ConfirmService } from './confirm.service';

describe('ConfirmService', () => {
  let service: ConfirmService;

  beforeEach(() => (service = TestBed.inject(ConfirmService)));

  it('resolves with the user answer and closes', async () => {
    const answer = service.confirm({ title: 'Delete?', message: 'Sure?' });
    expect(service.pending()?.title).toBe('Delete?');

    service.answer(true);

    await expect(answer).resolves.toBe(true);
    expect(service.pending()).toBeNull();
  });

  it('cancels a previous question when a new one is asked', async () => {
    const first = service.confirm({ title: 'First', message: '' });
    const second = service.confirm({ title: 'Second', message: '' });

    await expect(first).resolves.toBe(false);
    service.answer(true);
    await expect(second).resolves.toBe(true);
  });
});
