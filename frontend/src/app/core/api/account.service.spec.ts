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

  it('looks up a recipient by CBU or alias', () => {
    service.lookup(' sol.rio.mate ').subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/accounts/lookup');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('key')).toBe('sol.rio.mate');
  });

  it('changes the alias of an account', () => {
    service.changeAlias(5, ' nuevo.alias ').subscribe();
    const req = httpTesting.expectOne('/api/accounts/5/alias');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ alias: 'nuevo.alias' });
  });

  it('closes an account', () => {
    service.closeAccount(5).subscribe();
    expect(httpTesting.expectOne('/api/accounts/5').request.method).toBe('DELETE');
  });
});
