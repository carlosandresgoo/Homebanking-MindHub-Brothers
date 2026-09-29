import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { ClientLoan } from '../../core/models/loan.model';
import { ACCOUNTS, CATALOG, PERSONAL_LOAN } from '../../testing/loan-fixtures';
import { provideTestDefaults } from '../../testing/providers';
import { Loans } from './loans';

describe('Loans', () => {
  let httpTesting: HttpTestingController;
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    dialog.open.mockReset();
    await TestBed.configureTestingModule({
      imports: [Loans],
      providers: [...provideTestDefaults(), { provide: MatDialog, useValue: dialog }],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  function flushAll(loans: ClientLoan[]) {
    httpTesting.expectOne('/api/loans').flush(CATALOG);
    httpTesting.expectOne('/api/clients/current/loans').flush(loans);
    httpTesting.expectOne('/api/clients/current/accounts').flush(ACCOUNTS);
  }

  async function render(loans: ClientLoan[]) {
    const fixture = TestBed.createComponent(Loans);
    fixture.detectChanges();
    flushAll(loans);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows my loan with progress, outstanding and next installment', async () => {
    const { el } = await render([PERSONAL_LOAN]);
    const card = el.querySelector('.loan-card')!;
    expect(card.textContent).toContain('Préstamo Personal');
    expect(card.textContent).toContain('2 de 12 cuotas pagas');
    expect(card.textContent).toMatch(/Te falta pagar\s*\$\s*30\.000,00/);
    expect(card.textContent).toMatch(/Próxima cuota\s*\$\s*3\.000,00/);
    expect(card.querySelector('.pay-button')?.textContent).toContain('Pagar cuota 3');
  });

  it('marks paid-off loans and hides the pay button', async () => {
    const { el } = await render([
      { ...PERSONAL_LOAN, paymentsMade: 12, outstanding: 0, nextInstallment: 0, paidOff: true },
    ]);
    expect(el.querySelector('.badge')?.textContent).toContain('Cancelado');
    expect(el.querySelector('.pay-button')).toBeNull();
    expect(el.textContent).toMatch(/Total pagado\s*\$\s*36\.000,00/);
  });

  it('lists the catalog and blocks products with an active loan', async () => {
    const { el } = await render([PERSONAL_LOAN]);
    const products = el.querySelectorAll('.product-card');
    expect(products).toHaveLength(3);
    expect(products[0].textContent).toContain('Hipotecario');
    expect(products[0].textContent).toContain('12 a 60 cuotas');
    expect(products[0].textContent).toContain('Interés 20');
    const personal = el.querySelector<HTMLButtonElement>(
      '[aria-label="Solicitar préstamo Personal"]',
    )!;
    expect(personal.disabled).toBe(true);
    expect(personal.textContent).toContain('Ya tenés uno activo');
  });

  it('opens the application with the chosen product and reloads after approval', async () => {
    const { fixture, el } = await render([]);
    dialog.open.mockReturnValue({
      afterClosed: () => of({ ...PERSONAL_LOAN, id: 9, loanId: 3, name: 'Automotor' }),
    });

    el.querySelector<HTMLButtonElement>('[aria-label="Solicitar préstamo Automotor"]')!.click();

    const data = dialog.open.mock.calls[0][1].data;
    expect(data.preselectedLoanId).toBe(3);
    expect(data.accounts).toEqual(ACCOUNTS);
    flushAll([PERSONAL_LOAN]);
    await fixture.whenStable();
  });

  it('opens the payment dialog for the loan and reloads after paying', async () => {
    const { fixture, el } = await render([PERSONAL_LOAN]);
    dialog.open.mockReturnValue({ afterClosed: () => of({ ...PERSONAL_LOAN, paymentsMade: 3 }) });

    el.querySelector<HTMLButtonElement>('.pay-button')!.click();

    expect(dialog.open.mock.calls[0][1].data.loan).toEqual(PERSONAL_LOAN);
    flushAll([{ ...PERSONAL_LOAN, paymentsMade: 3 }]);
    await fixture.whenStable();
    expect(el.textContent).toContain('3 de 12 cuotas pagas');
  });
});
