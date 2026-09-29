import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Account, AccountDetail } from '../models/account.model';
import { idempotencyHeaders } from './idempotency';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  getMyAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.api}/clients/current/accounts`);
  }

  /** Owner or ADMIN; 404 otherwise. */
  getAccount(id: number): Observable<AccountDetail> {
    return this.http.get<AccountDetail>(`${this.api}/accounts/${id}`);
  }

  /** CLIENT only; 409 when the client already has the maximum of active accounts. */
  openAccount(idempotencyKey?: string): Observable<Account> {
    return this.http.post<Account>(`${this.api}/clients/current/accounts`, null, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }

  /** Owner only; 409 unless the balance is zero. */
  closeAccount(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/accounts/${id}`);
  }
}
