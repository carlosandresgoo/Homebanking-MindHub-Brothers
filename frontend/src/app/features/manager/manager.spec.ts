import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Client } from '../../core/models/client.model';
import { Manager } from './manager';

const CLIENTS: Client[] = [
  { id: 1, name: 'Melba', lastName: 'Morel', email: 'melba@gmail.com', accounts: [] },
];

describe('Manager', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Manager],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function renderLoaded() {
    const fixture = TestBed.createComponent(Manager);
    fixture.detectChanges();
    httpTesting.expectOne('/api/clients').flush(CLIENTS);
    await fixture.whenStable();
    return fixture;
  }

  it('lists clients and shows the raw response', async () => {
    const fixture = await renderLoaded();
    const el = fixture.nativeElement as HTMLElement;

    const rows = el.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('melba@gmail.com');
    expect(el.querySelector('.request')?.textContent).toContain('"email": "melba@gmail.com"');
  });

  it('flags invalid fields on submit and sends no request', async () => {
    const fixture = await renderLoaded();
    const el = fixture.nativeElement as HTMLElement;

    const name = el.querySelector<HTMLInputElement>('#name')!;
    name.value = 'Melba1';
    name.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(name.classList).toContain('is-invalid');
    expect(el.querySelector('#email')!.classList).toContain('is-invalid');
    httpTesting.expectNone('/api/clients');
  });
});
