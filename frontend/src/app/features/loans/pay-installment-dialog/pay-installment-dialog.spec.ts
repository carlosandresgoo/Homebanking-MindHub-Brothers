import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { ACCOUNTS, PERSONAL_LOAN } from '../../../testing/loan-fixtures';
import { provideTestDefaults } from '../../../testing/providers';
import { PayInstallmentDialog } from './pay-installment-dialog';

describe('PayInstallmentDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  beforeEach(async () => {
    dialogRef.close.mockReset();
    await TestBed.configureTestingModule({
      imports: [PayInstallmentDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { loan: PERSONAL_LOAN, accounts: ACCOUNTS } },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render() {
    const fixture = TestBed.createComponent(PayInstallmentDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows which installment is paid and pays it from an account with funds', async () => {
    const { el } = await render();
    expect(el.textContent).toContain('Cuota 3 de 12');
    expect(el.querySelector('.submit')?.textContent).toMatch(/Pagar \$\s*3\.000,00/);

    el.querySelector<HTMLButtonElement>('.submit')!.click();
    const req = httpTesting.expectOne('/api/clients/current/loans/7/payments');
    // VIN002 (1,000) cannot cover 3,000, so VIN001 is preselected.
    expect(req.request.body).toEqual({ accountNumber: 'VIN001' });
    req.flush({ ...PERSONAL_LOAN, paymentsMade: 3 });
    expect(dialogRef.close).toHaveBeenCalledWith({ ...PERSONAL_LOAN, paymentsMade: 3 });
  });

  it('explains insufficient funds', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('.submit')!.click();
    httpTesting
      .expectOne('/api/clients/current/loans/7/payments')
      .flush(null, { status: 422, statusText: 'Unprocessable Entity' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('saldo suficiente');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('retries a failed payment with the same Idempotency-Key', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('.submit')!.click();
    const first = httpTesting.expectOne('/api/clients/current/loans/7/payments');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    first.error(new ProgressEvent('error'));
    await fixture.whenStable();

    el.querySelector<HTMLButtonElement>('.submit')!.click();
    const retry = httpTesting.expectOne('/api/clients/current/loans/7/payments');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({ ...PERSONAL_LOAN, paymentsMade: 3 });
  });
});
