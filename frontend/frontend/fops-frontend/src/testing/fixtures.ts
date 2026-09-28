import {
  DashboardSummary,
  InventoryMovement,
  InventoryMovementDetail,
  Item,
  Order,
  OrderNotification,
  OrderProgress,
  User
} from '../app/core/models';

/**
 * Test data builders: sensible defaults, override only what the test cares about.
 */
export const aUser = (overrides: Partial<User> = {}): User => ({
  id: 1,
  name: 'Ana',
  email: 'ana@test.local',
  ...overrides
});

export const anItem = (overrides: Partial<Item> = {}): Item => ({
  id: 1,
  name: 'Laptop',
  sku: 'LAP-001',
  stockOnHand: 10,
  ...overrides
});

export const anOrder = (overrides: Partial<Order> = {}): Order => ({
  id: 1,
  userId: 1,
  itemId: 1,
  requestedQuantity: 10,
  fulfilledQuantity: 0,
  remainingQuantity: 10,
  completionPercent: 0,
  status: 'PENDING',
  createdAt: '2026-09-26T10:00:00',
  completedAt: null,
  ...overrides
});

export const aMovement = (overrides: Partial<InventoryMovement> = {}): InventoryMovement => ({
  id: 1,
  itemId: 1,
  orderId: null,
  sourceMovementId: null,
  completesOrder: false,
  quantity: 10,
  movementType: 'IN',
  reason: 'Supplier delivery',
  createdAt: '2026-09-26T10:00:00',
  ...overrides
});

export const aNotification = (overrides: Partial<OrderNotification> = {}): OrderNotification => ({
  status: 'SENT',
  recipient: 'ana@test.local',
  attempts: 1,
  lastAttemptAt: '2026-09-26T10:05:00',
  nextAttemptAt: null,
  sentAt: '2026-09-26T10:05:00',
  lastError: null,
  ...overrides
});

export const aProgress = (overrides: Partial<OrderProgress> = {}): OrderProgress => ({
  orderId: 2,
  userId: 1,
  userEmail: 'ana@test.local',
  itemId: 1,
  itemSku: 'LAP-001',
  requestedQuantity: 5,
  fulfilledQuantity: 5,
  remainingQuantity: 0,
  completionPercent: 100,
  status: 'COMPLETED',
  createdAt: '2026-09-26T10:00:00',
  completedAt: '2026-09-26T10:05:00',
  allocationCount: 2,
  completedBySingleMovement: false,
  notificationSent: true,
  notificationSentAt: '2026-09-26T10:05:00',
  notification: aNotification(),
  allocations: [
    { movementId: 3, sourceMovementId: 1, quantity: 2, allocatedAt: '2026-09-26T10:01:00', cumulativeFulfilled: 2, cumulativePercent: 40, completesOrder: false },
    { movementId: 5, sourceMovementId: 4, quantity: 3, allocatedAt: '2026-09-26T10:05:00', cumulativeFulfilled: 5, cumulativePercent: 100, completesOrder: true }
  ],
  ...overrides
});

export const aMovementDetail = (overrides: Partial<InventoryMovementDetail> = {}): InventoryMovementDetail => ({
  id: 1,
  movementType: 'IN',
  quantity: 10,
  reason: 'Supplier A',
  createdAt: '2026-09-26T10:00:00',
  itemId: 1,
  itemName: 'Laptop',
  itemSku: 'LAP-001',
  order: null,
  sourceMovementId: null,
  completesOrder: false,
  allocations: [
    aMovement({ id: 2, movementType: 'OUT', orderId: 1, sourceMovementId: 1, quantity: 8, completesOrder: true }),
    aMovement({ id: 3, movementType: 'OUT', orderId: 2, sourceMovementId: 1, quantity: 2 })
  ],
  ...overrides
});

export const aSummary = (overrides: Partial<DashboardSummary> = {}): DashboardSummary => ({
  totalUsers: 3,
  totalItems: 4,
  totalStockOnHand: 42,
  itemsOutOfStock: 1,
  totalOrders: 9,
  pendingOrders: 2,
  partiallyFulfilledOrders: 1,
  completedOrders: 6,
  openDemand: 17,
  notificationsSent: 5,
  notificationsPending: 1,
  notificationsFailed: 0,
  ...overrides
});
