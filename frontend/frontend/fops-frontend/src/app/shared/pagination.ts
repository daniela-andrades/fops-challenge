export const PAGE_SIZES = [10, 25, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 25;

export function pageCount(total: number, pageSize: number): number {
  return Math.max(1, Math.ceil(total / pageSize));
}

/** The rows of one page. Out-of-range pages are clamped, so a shrinking filter never shows an empty page. */
export function pageOf<T>(rows: readonly T[], page: number, pageSize: number): T[] {
  const current = Math.min(Math.max(1, page), pageCount(rows.length, pageSize));
  return rows.slice((current - 1) * pageSize, current * pageSize);
}

/** Case-insensitive "contains any of these fields" match; an empty query matches everything. */
export function matchesSearch(query: string, ...fields: (string | number | null | undefined)[]): boolean {
  const needle = query.trim().toLowerCase();
  return !needle || fields.some((field) => field !== null && field !== undefined && String(field).toLowerCase().includes(needle));
}
