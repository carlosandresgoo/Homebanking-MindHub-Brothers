import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { MovementQuery, MovementReceipt, Transaction } from '../models/account.model';
import { Page } from '../models/page.model';

/** An account's movements (owner or ADMIN; 404 otherwise). */
@Injectable({ providedIn: 'root' })
export class MovementService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** Newest first. */
  page(
    accountId: number,
    query: MovementQuery,
    page: number,
    size: number,
  ): Observable<Page<Transaction>> {
    const params = toParams(query).set('page', page).set('size', size);
    return this.http.get<Page<Transaction>>(`${this.api}/accounts/${accountId}/transactions`, {
      params,
    });
  }

  /** The same filters as a CSV file (the response carries its file name). */
  exportCsv(accountId: number, query: MovementQuery): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.api}/accounts/${accountId}/transactions/export`, {
      params: toParams(query),
      responseType: 'blob',
      observe: 'response',
    });
  }

  receipt(id: number): Observable<MovementReceipt> {
    return this.http.get<MovementReceipt>(`${this.api}/transactions/${id}`);
  }
}

function toParams(query: MovementQuery): HttpParams {
  let params = new HttpParams();
  for (const key of ['from', 'to', 'type', 'category', 'q'] as const) {
    const value = query[key]?.trim();
    if (value) params = params.set(key, value);
  }
  return params;
}
