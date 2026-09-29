import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import { LoginRequest, Role, TokenResponse } from '../models/auth.model';

export const AUTH_URL = `${environment.apiUrl}/auth`;

/**
 * Holds the session. The access token lives only in memory (a signal), never in localStorage, so an
 * XSS cannot read it from storage. The refresh token is an HttpOnly cookie the browser sends to
 * /api/auth/* on its own; on page reload the session is restored with a refresh call.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly token = signal<string | null>(null);
  private readonly currentRole = signal<Role | null>(null);
  private refreshInFlight: Observable<string> | null = null;

  readonly accessToken = this.token.asReadonly();
  readonly role = this.currentRole.asReadonly();
  readonly isAuthenticated = computed(() => this.token() !== null);

  login(credentials: LoginRequest): Observable<Role> {
    return this.http
      .post<TokenResponse>(`${AUTH_URL}/login`, credentials)
      .pipe(map((response) => this.startSession(response).role));
  }

  /** Concurrent callers share one request, so a burst of 401s triggers a single rotation. */
  refresh(): Observable<string> {
    this.refreshInFlight ??= this.http.post<TokenResponse>(`${AUTH_URL}/refresh`, null).pipe(
      map((response) => this.startSession(response).accessToken),
      finalize(() => (this.refreshInFlight = null)),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  /** Called once at startup: silently resumes a session if a valid refresh cookie exists. */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.refresh().pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${AUTH_URL}/logout`, null).pipe(
      catchError(() => of(undefined)),
      map(() => undefined),
      finalize(() => {
        this.clearSession();
        void this.router.navigateByUrl('/');
      }),
    );
  }

  /** The refresh token is gone or revoked: drop local state and ask the user to log in again. */
  expireSession(): void {
    this.clearSession();
    void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
  }

  private startSession(response: TokenResponse): TokenResponse {
    this.token.set(response.accessToken);
    this.currentRole.set(response.role);
    return response;
  }

  private clearSession(): void {
    this.token.set(null);
    this.currentRole.set(null);
  }
}
