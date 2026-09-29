import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Client, TwoFactorSetup } from '../models/client.model';

/** The logged-in client's authenticator-app second factor (TOTP). */
@Injectable({ providedIn: 'root' })
export class TwoFactorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clients/current/2fa`;

  /** Step 1: a new secret to scan; not active until {@link enable}. 409 if already enabled. */
  setup(): Observable<TwoFactorSetup> {
    return this.http.post<TwoFactorSetup>(`${this.baseUrl}/setup`, null);
  }

  /** Step 2: confirms with a code from the app. 403 (secondFactor INVALID) for a wrong code. */
  enable(code: string): Observable<Client> {
    return this.http.post<Client>(`${this.baseUrl}/enable`, { code });
  }

  /** 422 for a wrong password, 403 for a wrong code. */
  disable(password: string, code: string): Observable<Client> {
    return this.http.post<Client>(`${this.baseUrl}/disable`, { password, code });
  }
}
