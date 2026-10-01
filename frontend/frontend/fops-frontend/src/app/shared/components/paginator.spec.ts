import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Paginator } from './paginator';

@Component({
  imports: [Paginator],
  template: `<app-paginator [total]="total()" [(page)]="page" [(pageSize)]="pageSize" />`
})
class Host {
  readonly total = signal(53);
  readonly page = signal(1);
  readonly pageSize = signal(25);
}

describe('Paginator', () => {
  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const text = (selector: string) => host.querySelector(selector)?.textContent?.trim();
    const button = (selector: string) => host.querySelector<HTMLButtonElement>(selector)!;
    return { fixture, host, state: fixture.componentInstance, text, button };
  }

  it('shows the range and the position', () => {
    const { text, button } = render();

    expect(text('.range')).toBe('1–25 of 53');
    expect(text('.position')).toBe('Page 1 of 3');
    expect(button('.prev').disabled).toBe(true);
    expect(button('.next').disabled).toBe(false);
  });

  it('moves between pages through two-way binding', () => {
    const { fixture, state, text, button } = render();

    button('.next').click();
    fixture.detectChanges();
    button('.next').click();
    fixture.detectChanges();

    expect(state.page()).toBe(3);
    expect(text('.range')).toBe('51–53 of 53');
    expect(button('.next').disabled).toBe(true);
  });

  it('changing rows per page goes back to the first page', () => {
    const { fixture, state, host, text } = render();
    state.page.set(2);
    fixture.detectChanges();

    const select = host.querySelector<HTMLSelectElement>('select')!;
    select.value = '10';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(state.pageSize()).toBe(10);
    expect(state.page()).toBe(1);
    expect(text('.position')).toBe('Page 1 of 6');
  });

  it('renders nothing for an empty list', () => {
    const { fixture, state, host } = render();
    state.total.set(0);
    fixture.detectChanges();

    expect(host.querySelector('.paginator')).toBeNull();
  });
});
