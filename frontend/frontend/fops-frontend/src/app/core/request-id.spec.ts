import { newRequestId } from './request-id';

describe('newRequestId', () => {
  const uuidShape = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$/;

  afterEach(() => vi.unstubAllGlobals());

  it('returns a different UUID every time', () => {
    const ids = new Set(Array.from({ length: 50 }, () => newRequestId()));

    expect(ids.size).toBe(50);
    ids.forEach((id) => expect(id).toMatch(uuidShape));
  });

  it('falls back to a UUID-shaped id when randomUUID is unavailable (non-secure context)', () => {
    vi.stubGlobal('crypto', {});

    expect(newRequestId()).toMatch(uuidShape);
    expect(newRequestId()).not.toBe(newRequestId());
  });
});
