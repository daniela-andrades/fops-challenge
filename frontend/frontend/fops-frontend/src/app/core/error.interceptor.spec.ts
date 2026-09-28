import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { errorInterceptor } from './error.interceptor';
import { ToastService } from './toast.service';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let toasts: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([errorInterceptor])), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    toasts = TestBed.inject(ToastService);
  });

  const lastToast = () => toasts.toasts().at(-1);

  it('shows the API error message and still propagates the error', () => {
    const onError = vi.fn();
    http.get('/api/orders/9').subscribe({ error: onError });

    backend.expectOne('/api/orders/9').flush(
      { status: 404, message: 'Order 9 not found', fieldErrors: null },
      { status: 404, statusText: 'Not Found' }
    );

    expect(lastToast()).toMatchObject({ kind: 'error', message: 'Order 9 not found' });
    expect(onError).toHaveBeenCalled();
  });

  it('joins field validation errors', () => {
    http.post('/api/orders', {}).subscribe({ error: () => undefined });

    backend.expectOne('/api/orders').flush(
      { message: 'Invalid request data', fieldErrors: { userId: 'User is required', requestedQuantity: 'Quantity must be greater than 0' } },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(lastToast()?.message).toBe('User is required · Quantity must be greater than 0');
  });

  it('explains when the backend is unreachable', () => {
    http.get('/api/users').subscribe({ error: () => undefined });

    backend.expectOne('/api/users').error(new ProgressEvent('error'), { status: 0 });

    expect(lastToast()?.message).toBe('Cannot reach the API. Is the backend running?');
  });

  it('falls back to the status code when the body has no message', () => {
    http.get('/api/users').subscribe({ error: () => undefined });

    backend.expectOne('/api/users').flush(null, { status: 502, statusText: 'Bad Gateway' });

    expect(lastToast()?.message).toBe('Unexpected error (502)');
  });
});
