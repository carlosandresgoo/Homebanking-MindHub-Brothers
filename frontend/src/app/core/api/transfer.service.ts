import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { TransferReceipt, TransferRequest } from '../models/transfer.model';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);

  /** 404: source not mine / destination unknown; 422: insufficient funds or same account. */
  transfer(request: TransferRequest): Observable<TransferReceipt> {
    return this.http.post<TransferReceipt>(`${environment.apiUrl}/transfers`, request);
  }
}
