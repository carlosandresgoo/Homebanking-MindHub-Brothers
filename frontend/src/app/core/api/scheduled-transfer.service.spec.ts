import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { ScheduledTransferService } from './scheduled-transfer.service';

describe('ScheduledTransferService', () => {
  const base = '/api/clients/current/scheduled-transfers';
  let service: ScheduledTransferService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    service = TestBed.inject(ScheduledTransferService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lists and creates with an Idempotency-Key', () => {
    service.getMine().subscribe();
    expect(httpTesting.expectOne(base).request.method).toBe('GET');

    const request = {
      sourceAccountNumber: 'VIN001',
      targetAccountNumber: 'sol.rio.mate',
      amount: 100,
      frequency: 'MONTHLY' as const,
      startDate: '2026-10-01',
    };
    service.create(request, 'key-12345678').subscribe();
    const req = httpTesting.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-12345678');
  });

  it('pauses, resumes and cancels', () => {
    service.pause(3).subscribe();
    expect(httpTesting.expectOne(`${base}/3/pause`).request.method).toBe('POST');
    service.resume(3).subscribe();
    expect(httpTesting.expectOne(`${base}/3/resume`).request.method).toBe('POST');
    service.cancel(3).subscribe();
    expect(httpTesting.expectOne(`${base}/3`).request.method).toBe('DELETE');
  });
});
