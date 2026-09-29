import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Client, CreateClientRequest } from '../models/client.model';
import { ClientService } from './client.service';

describe('ClientService', () => {
  let service: ClientService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClientService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('getClients() requests GET /api/clients', () => {
    const clients: Client[] = [
      {
        id: 1,
        name: 'Melba',
        lastName: 'Morel',
        email: 'melba@gmail.com',
        role: 'CLIENT',
        accounts: [],
      },
    ];
    let result: Client[] | undefined;

    service.getClients().subscribe((c) => (result = c));

    const req = httpTesting.expectOne('/api/clients');
    expect(req.request.method).toBe('GET');
    req.flush(clients);
    expect(result).toEqual(clients);
  });

  it('getClient(id) requests GET /api/clients/{id}', () => {
    service.getClient(7).subscribe();
    expect(httpTesting.expectOne('/api/clients/7').request.method).toBe('GET');
  });

  it('getCurrentClient() requests GET /api/clients/current', () => {
    service.getCurrentClient().subscribe();
    expect(httpTesting.expectOne('/api/clients/current').request.method).toBe('GET');
  });

  it('createClient() posts the request body to /api/clients', () => {
    const body: CreateClientRequest = {
      name: 'Chloe',
      lastName: 'Obrian',
      email: 'chloe@test.com',
      password: 'a-long-enough-password',
    };
    service.createClient(body).subscribe();

    const req = httpTesting.expectOne('/api/clients');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
  });
});
