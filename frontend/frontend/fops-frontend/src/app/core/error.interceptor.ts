import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ApiError } from './models';
import { ToastService } from './toast.service';

/**
 * Shows the user the error message returned by the API (validation, business rules, conflicts).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toasts = inject(ToastService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      toasts.error(describe(error));
      return throwError(() => error);
    })
  );
};

function describe(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Cannot reach the API. Is the backend running?';
  }

  const body = error.error as Partial<ApiError> | null;
  if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
    return Object.values(body.fieldErrors).join(' · ');
  }
  return body?.message ?? `Unexpected error (${error.status})`;
}
