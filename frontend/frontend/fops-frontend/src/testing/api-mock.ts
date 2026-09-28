import { Provider } from '@angular/core';
import { of } from 'rxjs';
import { Mocked, vi } from 'vitest';
import { ApiService } from '../app/core/api.service';
import { aMovementDetail, aProgress, aSummary } from './fixtures';

export type ApiMock = Mocked<ApiService>;

/**
 * ApiService double where every call succeeds synchronously with empty or default data.
 * Tests override individual methods with mockReturnValue(of(...)) or throwError(...).
 */
export function createApiMock(): ApiMock {
  return {
    getUsers: vi.fn(() => of([])),
    getItems: vi.fn(() => of([])),
    getOrders: vi.fn(() => of([])),
    getOrderProgress: vi.fn(() => of(aProgress())),
    retryOrderNotification: vi.fn(() => of(aProgress())),
    getMovements: vi.fn(() => of([])),
    getMovement: vi.fn(() => of(aMovementDetail())),
    getDashboardSummary: vi.fn(() => of(aSummary())),
    createUser: vi.fn(),
    createItem: vi.fn(),
    createOrder: vi.fn(),
    registerIncomingInventory: vi.fn()
  } as unknown as ApiMock;
}

export function provideApiMock(api: ApiMock): Provider {
  return { provide: ApiService, useValue: api };
}
