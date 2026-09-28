import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  DashboardSummary,
  InventoryMovement,
  InventoryMovementDetail,
  Item,
  Order,
  OrderFilters,
  OrderProgress,
  User
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${this.baseUrl}/users`);
  }

  getItems(): Observable<Item[]> {
    return this.http.get<Item[]>(`${this.baseUrl}/items`);
  }

  getOrders(filters: OrderFilters = {}): Observable<Order[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Order[]>(`${this.baseUrl}/orders`, { params });
  }

  getOrderProgress(orderId: number): Observable<OrderProgress> {
    return this.http.get<OrderProgress>(`${this.baseUrl}/orders/${orderId}/progress`);
  }

  retryOrderNotification(orderId: number): Observable<OrderProgress> {
    return this.http.post<OrderProgress>(`${this.baseUrl}/orders/${orderId}/notification/retry`, {});
  }

  getMovements(itemId?: number | null): Observable<InventoryMovement[]> {
    const params = itemId ? new HttpParams().set('itemId', itemId) : undefined;
    return this.http.get<InventoryMovement[]>(`${this.baseUrl}/inventory/movements`, { params });
  }

  getMovement(movementId: number): Observable<InventoryMovementDetail> {
    return this.http.get<InventoryMovementDetail>(`${this.baseUrl}/inventory/movements/${movementId}`);
  }

  getDashboardSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/dashboard/summary`);
  }

  createUser(payload: { name: string; email: string }): Observable<User> {
    return this.http.post<User>(`${this.baseUrl}/users`, payload);
  }

  createItem(payload: { name: string; sku: string; stockOnHand: number }): Observable<Item> {
    return this.http.post<Item>(`${this.baseUrl}/items`, payload);
  }

  createOrder(payload: { userId: number; itemId: number; requestedQuantity: number }): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/orders`, payload);
  }

  registerIncomingInventory(payload: { itemId: number; quantity: number; reason: string }): Observable<InventoryMovement> {
    return this.http.post<InventoryMovement>(`${this.baseUrl}/inventory/incoming`, payload);
  }
}
