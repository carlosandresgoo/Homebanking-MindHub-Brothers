import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Account, AccountDetail, Recipient } from '../models/account.model';
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

  /**
   * Who receives money sent to an account number, CBU or alias (CLIENT only, rate-limited).
   * 404 unknown; 422 with `code` INVALID_CBU or OTHER_BANK; 429 too many lookups.
   */
  lookup(key: string): Observable<Recipient> {
    return this.http.get<Recipient>(`${this.api}/accounts/lookup`, {
      params: new HttpParams().set('key', key.trim()),
    });
  }

  /** Owner only; 409 when another account uses it, 422 (`ALIAS_RESERVED`) for a reserved one. */
  changeAlias(id: number, alias: string): Observable<Account> {
    return this.http.patch<Account>(`${this.api}/accounts/${id}/alias`, { alias: alias.trim() });
  }

  /** Owner only; 409 unless the balance is zero. */
  closeAccount(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/accounts/${id}`);
  }
}
