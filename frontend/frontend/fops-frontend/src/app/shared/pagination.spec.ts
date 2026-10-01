import { matchesSearch, pageCount, pageOf } from './pagination';

describe('pagination helpers', () => {
  const rows = Array.from({ length: 53 }, (_, i) => i + 1);

  it('slices one page and counts pages', () => {
    expect(pageOf(rows, 1, 25)).toEqual(rows.slice(0, 25));
    expect(pageOf(rows, 3, 25)).toEqual([51, 52, 53]);
    expect(pageCount(53, 25)).toBe(3);
    expect(pageCount(0, 25)).toBe(1);
  });

  it('clamps pages outside the range instead of returning an empty page', () => {
    expect(pageOf(rows, 99, 25)).toEqual([51, 52, 53]);
    expect(pageOf(rows, 0, 25)).toEqual(rows.slice(0, 25));
    expect(pageOf([], 4, 25)).toEqual([]);
  });

  it('matches any field, case-insensitively, ignoring surrounding spaces', () => {
    expect(matchesSearch('  lap ', 'Laptop', 'LAP-001')).toBe(true);
    expect(matchesSearch('lap-001', 'Laptop', 'LAP-001')).toBe(true);
    expect(matchesSearch('mouse', 'Laptop', null, undefined, 42)).toBe(false);
    expect(matchesSearch('', 'anything')).toBe(true);
  });
});
