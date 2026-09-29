import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { AccountService } from './account.service';

describe('AccountService', () => {
  let service: AccountService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    service = TestBed.inject(AccountService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lists my accounts', () => {
    service.getMyAccounts().subscribe();
    expect(httpTesting.expectOne('/api/clients/current/accounts').request.method).toBe('GET');
  });

  it('gets an account with its movements', () => {
    service.getAccount(5).subscribe();
    expect(httpTesting.expectOne('/api/accounts/5').request.method).toBe('GET');
  });

  it('opens an account', () => {
    service.openAccount().subscribe();
    expect(httpTesting.expectOne('/api/clients/current/accounts').request.method).toBe('POST');
  });

  it('closes an account', () => {
    service.closeAccount(5).subscribe();
    expect(httpTesting.expectOne('/api/accounts/5').request.method).toBe('DELETE');
  });
});
