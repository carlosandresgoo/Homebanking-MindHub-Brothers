import { HttpTestingController } from '@angular/common/http/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSelectHarness } from '@angular/material/select/testing';

import { ACCOUNTS, CATALOG, PERSONAL_LOAN } from '../../../testing/loan-fixtures';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { ApplyLoanDialog, ApplyLoanDialogData } from './apply-loan-dialog';

describe('ApplyLoanDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  async function render(data: Partial<ApplyLoanDialogData> = {}) {
    dialogRef.close.mockReset();
    await TestBed.configureTestingModule({
      imports: [ApplyLoanDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { catalog: CATALOG, accounts: ACCOUNTS, activeLoanIds: [2], ...data },
        },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ApplyLoanDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => httpTesting.verify());

  it('disables products with an active loan and preselects the requested one', async () => {
    const { el } = await render({ preselectedLoanId: 3 });
    const products = Array.from(el.querySelectorAll<HTMLButtonElement>('.product'));
    expect(products[1].disabled).toBe(true);
    expect(products[1].textContent).toContain('Ya tenés uno activo');
    expect(products[2].getAttribute('aria-checked')).toBe('true');
  });

  it('simulates total and installment and applies', async () => {
    const { fixture, el } = await render({ preselectedLoanId: 3 });
    const loader = TestbedHarnessEnvironment.loader(fixture);
    typeInto(el, '#amount', '10000');
    await (
      await loader.getHarness(MatSelectHarness.with({ selector: '#payments' }))
    ).clickOptions({
      text: '12 cuotas',
    });
    await fixture.whenStable();

    const simulation = el.querySelector('.simulation')!.textContent!;
    expect(simulation).toMatch(/Total a devolver\s*\$\s*12\.000,00/);
    expect(simulation).toMatch(/Cuota estimada\s*\$\s*1\.000,00/);

    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    const req = httpTesting.expectOne('/api/loans');
    expect(req.request.body).toEqual({
      loanId: 3,
      amount: 10000,
      payments: 12,
      accountNumber: 'VIN001',
    });
    req.flush({ ...PERSONAL_LOAN, loanId: 3 });
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('rejects amounts above the product maximum', async () => {
    const { fixture, el } = await render({ preselectedLoanId: 3 });
    typeInto(el, '#amount', '300001');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(el.textContent).toContain('Supera el máximo');
    httpTesting.expectNone('/api/loans');
  });
});
