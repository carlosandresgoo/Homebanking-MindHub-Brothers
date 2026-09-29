import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

import { AUTH_URL } from '../auth/auth.service';

/**
 * Logs HTTP failures without the response body (it may contain personal data)
 * and re-throws so each caller decides how to present the error.
 * A failed silent refresh (no session yet) is expected and not logged.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && req.url !== `${AUTH_URL}/refresh`) {
        console.error(`HTTP ${error.status} ${req.method} ${req.urlWithParams}`);
      }
      return throwError(() => error);
    }),
  );
