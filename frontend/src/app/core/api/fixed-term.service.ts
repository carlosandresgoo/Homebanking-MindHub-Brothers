import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CreateFixedTermRequest, FixedTerm, FixedTermPlan } from '../models/fixed-term.model';
import { idempotencyHeaders } from './idempotency';

@Injectable({ providedIn: 'root' })
export class FixedTermService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  getPlans(): Observable<FixedTermPlan[]> {
    return this.http.get<FixedTermPlan[]>(`${this.api}/fixed-terms/plans`);
  }

  /** Active first (soonest maturity first), then paid. */
  getMine(): Observable<FixedTerm[]> {
    return this.http.get<FixedTerm[]>(`${this.api}/clients/current/fixed-terms`);
  }

  /** 404 account not mine; 422 minimum (`code` BELOW_MINIMUM), unknown term or no funds. */
  create(request: CreateFixedTermRequest, idempotencyKey?: string): Observable<FixedTerm> {
    return this.http.post<FixedTerm>(`${this.api}/clients/current/fixed-terms`, request, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }

  /** 422 once paid. */
  setAutoRenew(id: number, autoRenew: boolean): Observable<FixedTerm> {
    return this.http.patch<FixedTerm>(`${this.api}/clients/current/fixed-terms/${id}`, {
      autoRenew,
    });
  }
}
