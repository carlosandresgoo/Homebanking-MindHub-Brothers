import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Client } from '../../core/models/client.model';
import { Accounts } from './accounts';

describe('Accounts', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  function render() {
    const fixture = TestBed.createComponent(Accounts);
    fixture.detectChanges();
    return fixture;
  }

  it('shows a loading message until the request completes', () => {
    const fixture = render();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Loading');
    httpTesting.expectOne('/api/clients').flush([]);
  });

  it('renders each client with its accounts', async () => {
    const fixture = render();
    const clients: Client[] = [
      {
        id: 1,
        name: 'Melba',
        lastName: 'Morel',
        email: 'melba@gmail.com',
        accounts: [
          { id: 1, number: 'vin001', creationDate: '2026-09-29T10:24:55.635622', balance: 5000 },
          { id: 2, number: 'vin002', creationDate: '2026-09-30T10:24:55.643164', balance: 7500 },
        ],
      },
    ];

    httpTesting.expectOne('/api/clients').flush(clients);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.client-row')?.textContent).toContain('Name : Melba');
    const rows = Array.from(el.querySelectorAll('tbody tr:not(.client-row)'));
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('vin001');
    expect(rows[0].textContent).toContain('2026-09-29 at');
    expect(rows[0].textContent).toContain('10:24');
    expect(rows[0].textContent).toContain('$ 5000');
  });

  it('shows an error message when the request fails', async () => {
    const fixture = render();

    httpTesting
      .expectOne('/api/clients')
      .flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
  });
});
