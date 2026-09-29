import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { TransferService } from './transfer.service';

describe('TransferService', () => {
  it('posts the transfer', () => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    const httpTesting = TestBed.inject(HttpTestingController);
    const body = { sourceAccountNumber: 'VIN001', targetAccountNumber: 'VIN002', amount: 5 };

    TestBed.inject(TransferService).transfer(body).subscribe();

    const req = httpTesting.expectOne('/api/transfers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    httpTesting.verify();
  });
});
