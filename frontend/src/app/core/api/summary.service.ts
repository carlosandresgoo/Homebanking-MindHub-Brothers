import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { FinancialSummary } from '../models/summary.model';

@Injectable({ providedIn: 'root' })
export class SummaryService {
  private readonly http = inject(HttpClient);

  /** CLIENT only; `months` (1..12) includes the current month. */
  getSummary(months: number): Observable<FinancialSummary> {
    return this.http.get<FinancialSummary>(`${environment.apiUrl}/clients/current/summary`, {
      params: { months },
    });
  }
}
