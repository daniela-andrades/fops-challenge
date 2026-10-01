/**
 * A new Idempotency-Key. randomUUID needs a secure context (https or localhost); elsewhere fall back to a
 * random id of the same shape, which is still unique enough for de-duplicating retries.
 */
export function newRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  const hex = (length: number) => Array.from({ length }, () => Math.floor(Math.random() * 16).toString(16)).join('');
  return `${hex(8)}-${hex(4)}-4${hex(3)}-${hex(4)}-${hex(12)}`;
}
