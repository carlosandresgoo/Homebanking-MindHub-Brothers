import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ClientLoan, Loan, LoanApplication } from '../models/loan.model';

@Injectable({ providedIn: 'root' })
export class LoanService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  getCatalog(): Observable<Loan[]> {
    return this.http.get<Loan[]>(`${this.api}/loans`);
  }

  getMyLoans(): Observable<ClientLoan[]> {
    return this.http.get<ClientLoan[]>(`${this.api}/clients/current/loans`);
  }

  /** 409: already an active loan of that product; 422: amount/installments not allowed. */
  apply(application: LoanApplication): Observable<ClientLoan> {
    return this.http.post<ClientLoan>(`${this.api}/loans`, application);
  }

  /** Pays the next installment; 422 when paid off or without funds. */
  payInstallment(clientLoanId: number, accountNumber: string): Observable<ClientLoan> {
    return this.http.post<ClientLoan>(
      `${this.api}/clients/current/loans/${clientLoanId}/payments`,
      { accountNumber },
    );
  }
}
