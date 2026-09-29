import { TestBed } from '@angular/core/testing';
import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  it.each([
    ['PENDING', 'Pending'],
    ['PARTIALLY_FULFILLED', 'Partial'],
    ['COMPLETED', 'Completed'],
    ['IN', 'In'],
    ['OUT', 'Out']
  ] as const)('shows %s as "%s"', (value, label) => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector('.badge')!;
    expect(badge.textContent).toBe(label);
    expect(badge.classList).toContain(value);
  });

  it('shows cancelled orders as "Cancelled"', () => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('value', 'CANCELLED');
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector('.badge')!;
    expect(badge.textContent).toBe('Cancelled');
    expect(badge.classList).toContain('CANCELLED');
  });
});
