import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { LoanService } from './loan.service';

describe('LoanService', () => {
  let service: LoanService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    service = TestBed.inject(LoanService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('gets the catalog and my loans', () => {
    service.getCatalog().subscribe();
    service.getMyLoans().subscribe();
    expect(httpTesting.expectOne('/api/loans').request.method).toBe('GET');
    expect(httpTesting.expectOne('/api/clients/current/loans').request.method).toBe('GET');
  });

  it('applies for a loan', () => {
    const application = { loanId: 2, amount: 1000, payments: 6, accountNumber: 'VIN001' };
    service.apply(application).subscribe();
    const req = httpTesting.expectOne('/api/loans');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(application);
  });

  it('pays the next installment', () => {
    service.payInstallment(7, 'VIN002').subscribe();
    const req = httpTesting.expectOne('/api/clients/current/loans/7/payments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ accountNumber: 'VIN002' });
  });
});
