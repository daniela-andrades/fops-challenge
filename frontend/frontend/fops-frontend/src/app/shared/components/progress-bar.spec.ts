import { TestBed } from '@angular/core/testing';
import { ProgressBar } from './progress-bar';

describe('ProgressBar', () => {
  function render(percent: number, status: 'PENDING' | 'PARTIALLY_FULFILLED' | 'COMPLETED' = 'PARTIALLY_FULFILLED') {
    const fixture = TestBed.createComponent(ProgressBar);
    fixture.componentRef.setInput('percent', percent);
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    return {
      fill: host.querySelector<HTMLElement>('.fill')!,
      label: host.querySelector('.label')!.textContent!.trim(),
      bar: host.querySelector('[role=progressbar]')!
    };
  }

  it('renders the width, label and accessible value', () => {
    const { fill, label, bar } = render(62.5);

    expect(fill.style.width).toBe('62.5%');
    expect(label).toBe('62.5%');
    expect(bar.getAttribute('aria-valuenow')).toBe('62.5');
  });

  it('clamps values outside 0-100', () => {
    expect(render(140).fill.style.width).toBe('100%');
    expect(render(-5).fill.style.width).toBe('0%');
  });

  it('colours the bar by order status', () => {
    expect(render(100, 'COMPLETED').fill.classList).toContain('COMPLETED');
    expect(render(30, 'PARTIALLY_FULFILLED').fill.classList).toContain('PARTIALLY_FULFILLED');
  });
});
