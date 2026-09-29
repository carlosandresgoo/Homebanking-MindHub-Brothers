import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Client } from '../../core/models/client.model';
import { Manager } from './manager';

const CLIENTS: Client[] = [
  { id: 1, name: 'Melba', lastName: 'Morel', email: 'melba@gmail.com', role: 'CLIENT', accounts: [] },
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

  function type(el: HTMLElement, selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
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

    type(el, '#name', 'Melba1');
    type(el, '#password', 'short');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(el.querySelector('#name')!.classList).toContain('is-invalid');
    expect(el.querySelector('#email')!.classList).toContain('is-invalid');
    expect(el.querySelector('#password')!.classList).toContain('is-invalid');
    httpTesting.expectNone({ method: 'POST', url: '/api/clients' });
  });

  it('creates a client and reloads the list', async () => {
    const fixture = await renderLoaded();
    const el = fixture.nativeElement as HTMLElement;

    type(el, '#name', 'Chloe');
    type(el, '#lastName', 'Obrian');
    type(el, '#email', 'chloe@test.com');
    type(el, '#password', 'a-long-enough-password');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    const create = httpTesting.expectOne({ method: 'POST', url: '/api/clients' });
    expect(create.request.body).toEqual({
      name: 'Chloe',
      lastName: 'Obrian',
      email: 'chloe@test.com',
      password: 'a-long-enough-password',
    });
    const created: Client = { id: 2, name: 'Chloe', lastName: 'Obrian', email: 'chloe@test.com', role: 'CLIENT', accounts: [] };
    create.flush(created);
    httpTesting.expectOne({ method: 'GET', url: '/api/clients' }).flush([...CLIENTS, created]);
    await fixture.whenStable();

    expect(el.querySelector('[role="status"]')?.textContent).toContain('chloe@test.com created');
    expect(el.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(el.querySelector<HTMLInputElement>('#password')!.value).toBe('');
  });

  it('shows a conflict message when the email already exists', async () => {
    const fixture = await renderLoaded();
    const el = fixture.nativeElement as HTMLElement;

    type(el, '#name', 'Melba');
    type(el, '#lastName', 'Morel');
    type(el, '#email', 'melba@gmail.com');
    type(el, '#password', 'a-long-enough-password');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    httpTesting
      .expectOne({ method: 'POST', url: '/api/clients' })
      .flush({ status: 409 }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(el.querySelector('[role="status"]')?.textContent).toContain('already registered');
  });
});
