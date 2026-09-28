export type OrderStatus = 'PENDING' | 'PARTIALLY_FULFILLED' | 'COMPLETED';
export type MovementType = 'IN' | 'OUT';
export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED';

export interface User {
  id: number;
  name: string;
  email: string;
  createdAt?: string;
}

export interface Item {
  id: number;
  name: string;
  sku: string;
  stockOnHand: number;
  createdAt?: string;
}

export interface Order {
  id: number;
  userId: number;
  itemId: number;
  requestedQuantity: number;
  fulfilledQuantity: number;
  remainingQuantity: number;
  completionPercent: number;
  status: OrderStatus;
  createdAt?: string;
  completedAt?: string | null;
}

export interface InventoryMovement {
  id: number;
  itemId: number;
  orderId: number | null;
  sourceMovementId: number | null;
  completesOrder: boolean;
  quantity: number;
  movementType: MovementType;
  reason: string;
  createdAt?: string;
}

export interface OrderAllocation {
  movementId: number;
  sourceMovementId: number | null;
  quantity: number;
  allocatedAt: string;
  cumulativeFulfilled: number;
  cumulativePercent: number;
  completesOrder: boolean;
}

export interface OrderNotification {
  status: NotificationStatus;
  recipient: string;
  attempts: number;
  lastAttemptAt: string | null;
  nextAttemptAt: string | null;
  sentAt: string | null;
  lastError: string | null;
}

export interface OrderProgress {
  orderId: number;
  userId: number;
  userEmail: string;
  itemId: number;
  itemSku: string;
  requestedQuantity: number;
  fulfilledQuantity: number;
  remainingQuantity: number;
  completionPercent: number;
  status: OrderStatus;
  createdAt: string;
  completedAt: string | null;
  allocationCount: number;
  completedBySingleMovement: boolean | null;
  notificationSent: boolean;
  notificationSentAt: string | null;
  notification: OrderNotification | null;
  allocations: OrderAllocation[];
}

export interface InventoryMovementDetail {
  id: number;
  movementType: MovementType;
  quantity: number;
  reason: string;
  createdAt: string;
  itemId: number;
  itemName: string;
  itemSku: string;
  order: Order | null;
  sourceMovementId: number | null;
  completesOrder: boolean;
  allocations: InventoryMovement[];
}

export interface DashboardSummary {
  totalUsers: number;
  totalItems: number;
  totalStockOnHand: number;
  itemsOutOfStock: number;
  totalOrders: number;
  pendingOrders: number;
  partiallyFulfilledOrders: number;
  completedOrders: number;
  openDemand: number;
  notificationsSent: number;
  notificationsPending: number;
  notificationsFailed: number;
}

export interface OrderFilters {
  userId?: number | null;
  itemId?: number | null;
  status?: OrderStatus | null;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fieldErrors: Record<string, string> | null;
}
