import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Client } from '../../core/models/client.model';
import { provideTestDefaults } from '../../testing/providers';
import { Accounts } from './accounts';

const MELBA: Client = {
  id: 1,
  name: 'Melba',
  lastName: 'Morel',
  email: 'melba@gmail.com',
  role: 'CLIENT',
  accounts: [
    { id: 1, number: 'vin001', creationDate: '2026-09-29T10:24:55.635622', balance: 5000 },
    { id: 2, number: 'vin002', creationDate: '2026-09-30T10:24:55.643164', balance: 7500 },
  ],
};

describe('Accounts', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Accounts],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  function render() {
    const fixture = TestBed.createComponent(Accounts);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows a progress bar and skeletons while loading', () => {
    const { el } = render();
    expect(el.querySelector('mat-progress-bar')).not.toBeNull();
    expect(el.querySelectorAll('.skeleton').length).toBeGreaterThan(0);
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
  });

  it('greets the client and shows the total and each account', async () => {
    const { fixture, el } = render();
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
    await fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('Hola, Melba');
    expect(el.querySelector('.summary-amount')?.textContent).toMatch(/12\.500,00/);
    expect(el.querySelector('.summary-meta')?.textContent).toContain('2 cuentas');

    const cards = el.querySelectorAll('.account-card');
    expect(cards).toHaveLength(2);
    expect(cards[0].textContent).toContain('VIN001');
    expect(cards[0].textContent).toMatch(/5\.000,00/);
    expect(cards[0].textContent).toContain('29 de septiembre de 2026');
  });

  it('shows an empty state when the client has no accounts', async () => {
    const { fixture, el } = render();
    httpTesting.expectOne('/api/clients/current').flush({ ...MELBA, accounts: [] });
    await fixture.whenStable();

    expect(el.textContent).toContain('Todavía no tenés cuentas');
    expect(el.querySelector('.summary-meta')?.textContent).toContain('0 cuentas');
  });

  it('shows an error with a retry button that reloads', async () => {
    const { fixture, el } = render();
    httpTesting
      .expectOne('/api/clients/current')
      .flush('boom', { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'No pudimos cargar tus cuentas',
    );
    el.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
    await fixture.whenStable();

    expect(el.querySelectorAll('.account-card')).toHaveLength(2);
  });

  it('links each account card to its detail page', async () => {
    const { fixture, el } = render();
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
    await fixture.whenStable();

    expect(el.querySelector('a.account-card')?.getAttribute('href')).toBe('/accounts/1');
  });

  it('opens a new account and reloads the list', async () => {
    const { fixture, el } = render();
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('.open-button')!.click();
    const req = httpTesting.expectOne({ method: 'POST', url: '/api/clients/current/accounts' });
    req.flush({ id: 3, number: 'VIN-12345678', creationDate: '2026-10-01T10:00:00', balance: 0 });

    const withNew: Client = {
      ...MELBA,
      accounts: [
        ...MELBA.accounts,
        { id: 3, number: 'VIN-12345678', creationDate: '2026-10-01T10:00:00', balance: 0 },
      ],
    };
    httpTesting.expectOne('/api/clients/current').flush(withNew);
    await fixture.whenStable();

    expect(el.querySelectorAll('.account-card')).toHaveLength(3);
  });

  it('disables "Abrir cuenta" at the limit of 3 active accounts', async () => {
    const { fixture, el } = render();
    const full: Client = {
      ...MELBA,
      accounts: [
        ...MELBA.accounts,
        { id: 3, number: 'VIN-3', creationDate: '2026-10-01T10:00:00', balance: 0 },
      ],
    };
    httpTesting.expectOne('/api/clients/current').flush(full);
    await fixture.whenStable();

    expect(el.querySelector<HTMLButtonElement>('.open-button')!.disabled).toBe(true);
    expect(el.querySelector('.limit-hint')?.textContent).toContain('máximo de 3 cuentas');
  });
});
