import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the order filters that are set', () => {
    api.getOrders({ userId: 3, itemId: null, status: 'PENDING' }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/orders');
    expect(req.request.params.get('userId')).toBe('3');
    expect(req.request.params.get('status')).toBe('PENDING');
    expect(req.request.params.has('itemId')).toBe(false);
    req.flush([]);
  });

  it('requests all orders without params by default', () => {
    api.getOrders().subscribe();

    const req = http.expectOne('/api/orders');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('filters movements by item only when given', () => {
    api.getMovements(7).subscribe();
    http.expectOne('/api/inventory/movements?itemId=7').flush([]);

    api.getMovements().subscribe();
    http.expectOne('/api/inventory/movements').flush([]);
  });

  it('uses the traceability and dashboard endpoints', () => {
    api.getOrderProgress(4).subscribe();
    api.getMovement(9).subscribe();
    api.getDashboardSummary().subscribe();

    http.expectOne('/api/orders/4/progress').flush({});
    http.expectOne('/api/inventory/movements/9').flush({});
    http.expectOne('/api/dashboard/summary').flush({});
  });

  it('updates and deletes master data and movements', () => {
    api.updateUser(3, { name: 'Ana', email: 'ana@test.local' }).subscribe();
    api.deleteUser(3).subscribe();
    api.updateItem(4, { name: 'Laptop', sku: 'LAP-1' }).subscribe();
    api.deleteItem(4).subscribe();
    api.updateMovement(5, { reason: 'Supplier A' }).subscribe();
    api.deleteMovement(5).subscribe();

    const expectations: [string, string, unknown][] = [
      ['/api/users/3', 'PUT', { name: 'Ana', email: 'ana@test.local' }],
      ['/api/users/3', 'DELETE', null],
      ['/api/items/4', 'PUT', { name: 'Laptop', sku: 'LAP-1' }],
      ['/api/items/4', 'DELETE', null],
      ['/api/inventory/movements/5', 'PUT', { reason: 'Supplier A' }],
      ['/api/inventory/movements/5', 'DELETE', null]
    ];
    for (const [url, method, body] of expectations) {
      const req = http.expectOne((r) => r.url === url && r.method === method);
      expect(req.request.body).toEqual(body);
      req.flush(null);
    }
  });

  it('posts create and command payloads', () => {
    api.createOrder({ userId: 1, itemId: 2, requestedQuantity: 3 }).subscribe();
    api.registerIncomingInventory({ itemId: 2, quantity: 5, reason: 'Supplier' }).subscribe();
    api.retryOrderNotification(4).subscribe();

    const order = http.expectOne('/api/orders');
    expect(order.request.method).toBe('POST');
    expect(order.request.body).toEqual({ userId: 1, itemId: 2, requestedQuantity: 3 });
    order.flush({});

    const incoming = http.expectOne('/api/inventory/incoming');
    expect(incoming.request.body).toEqual({ itemId: 2, quantity: 5, reason: 'Supplier' });
    incoming.flush({});

    const retry = http.expectOne('/api/orders/4/notification/retry');
    expect(retry.request.method).toBe('POST');
    retry.flush({});
  });
});
