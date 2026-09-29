import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { FixedTerm } from '../../../core/models/fixed-term.model';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { FixedTermDialog, FixedTermDialogData } from './fixed-term-dialog';

const URL = '/api/clients/current/fixed-terms';
const DATA: FixedTermDialogData = {
  plans: [
    { termDays: 30, annualRate: 0.35 },
    { termDays: 90, annualRate: 0.37 },
  ],
  accounts: [
    { id: 1, number: 'VIN001', creationDate: '2026-09-01T00:00:00', balance: 5000 },
    { id: 2, number: 'VIN002', creationDate: '2026-09-01T00:00:00', balance: 21500 },
  ],
};

describe('FixedTermDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  beforeEach(async () => {
    dialogRef.close.mockReset();
    await TestBed.configureTestingModule({
      imports: [FixedTermDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: DATA },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render() {
    const fixture = TestBed.createComponent(FixedTermDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, loader: TestbedHarnessEnvironment.loader(fixture) };
  }

  it('simulates the interest live and preselects the account with most money', async () => {
    const { fixture, el, loader } = await render();
    expect(el.textContent).toContain('para ver cuánto ganás');

    typeInto(el, '#ftAmount', '1000');
    await fixture.whenStable();
    expect(el.querySelector('.simulation')?.textContent).toMatch(/\+\s*\$\s*28,77/);
    expect(el.querySelector('.simulation')?.textContent).toMatch(/\$\s*1\.028,77/);

    const ninety = await loader.getHarness(MatButtonToggleHarness.with({ text: /90 días/ }));
    await ninety.check();
    // 1,000 × 37 % × 90 / 365 = 91.23
    expect(el.querySelector('.simulation')?.textContent).toMatch(/\+\s*\$\s*91,23/);
  });

  it('constitutes it with an Idempotency-Key', async () => {
    const { fixture, el, loader } = await render();
    typeInto(el, '#ftAmount', '2000');
    await (await loader.getHarness(MatSlideToggleHarness)).check();
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    const req = httpTesting.expectOne(URL);
    expect(req.request.body).toEqual({
      accountNumber: 'VIN002',
      amount: 2000,
      termDays: 30,
      autoRenew: true,
    });
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    const created = { id: 9, principal: 2000 } as FixedTerm;
    req.flush(created);
    expect(dialogRef.close).toHaveBeenCalledWith(created);
  });

  it('checks the minimum and the balance before calling the API', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#ftAmount', '999');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
    expect(el.textContent).toContain('El mínimo es');

    typeInto(el, '#ftAmount', '30000');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
    expect(el.textContent).toContain('Supera el saldo de la cuenta.');
    httpTesting.expectNone(URL);
  });

  it('explains a refusal from the API', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#ftAmount', '1000');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    httpTesting
      .expectOne(URL)
      .flush({ detail: 'Insufficient funds' }, { status: 422, statusText: 'Unprocessable Entity' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No tenés saldo suficiente');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
