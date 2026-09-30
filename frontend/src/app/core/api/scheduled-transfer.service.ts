import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import {
  CreateScheduledTransferRequest,
  ScheduledTransfer,
} from '../models/scheduled-transfer.model';
import { idempotencyHeaders } from './idempotency';

@Injectable({ providedIn: 'root' })
export class ScheduledTransferService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clients/current/scheduled-transfers`;

  getMine(): Observable<ScheduledTransfer[]> {
    return this.http.get<ScheduledTransfer[]>(this.baseUrl);
  }

  /**
   * 403 (secondFactor) when the amount to others needs a 2FA code; 404 unknown accounts; 422 with
   * `code` START_DATE, TOO_MANY_SCHEDULED, INVALID_CBU or OTHER_BANK. Same key = never twice.
   */
  create(
    request: CreateScheduledTransferRequest,
    idempotencyKey?: string,
  ): Observable<ScheduledTransfer> {
    return this.http.post<ScheduledTransfer>(this.baseUrl, request, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }

  pause(id: number): Observable<ScheduledTransfer> {
    return this.http.post<ScheduledTransfer>(`${this.baseUrl}/${id}/pause`, null);
  }

  resume(id: number): Observable<ScheduledTransfer> {
    return this.http.post<ScheduledTransfer>(`${this.baseUrl}/${id}/resume`, null);
  }

  cancel(id: number): Observable<ScheduledTransfer> {
    return this.http.delete<ScheduledTransfer>(`${this.baseUrl}/${id}`);
  }
}
