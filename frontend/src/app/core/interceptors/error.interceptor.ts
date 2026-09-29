import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Logs HTTP failures without the response body (it may contain personal data)
 * and re-throws so each caller decides how to present the error.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        console.error(`HTTP ${error.status} ${req.method} ${req.urlWithParams}`);
      }
      return throwError(() => error);
    }),
  );
