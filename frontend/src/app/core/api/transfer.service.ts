import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TransferLimits, TransferReceipt, TransferRequest } from '../models/transfer.model';
import { idempotencyHeaders } from './idempotency';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);

  /**
   * 404: source not mine / destination unknown; 422: insufficient funds or same account.
   * Retrying with the same `idempotencyKey` never transfers twice.
   */
  transfer(request: TransferRequest, idempotencyKey?: string): Observable<TransferReceipt> {
    return this.http.post<TransferReceipt>(`${environment.apiUrl}/transfers`, request, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }

  getLimits(): Observable<TransferLimits> {
    return this.http.get<TransferLimits>(`${environment.apiUrl}/transfers/limits`);
  }
}
