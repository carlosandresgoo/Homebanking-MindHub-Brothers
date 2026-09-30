import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { Account, Recipient } from '../../../core/models/account.model';
import { ScheduledTransfer } from '../../../core/models/scheduled-transfer.model';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { bankDate } from '../../../core/utils/bank-date';
import { ScheduleDialog, ScheduleDialogData } from './schedule-dialog';

const URL = '/api/clients/current/scheduled-transfers';
const ACCOUNTS: Account[] = [
  {
    id: 11,
    number: 'VIN001',
    cbu: '9990001800000000000017',
    alias: 'vin001.test',
    creationDate: '2026-08-30T00:00:00',
    balance: 5000,
  },
  {
    id: 12,
    number: 'VIN002',
    cbu: '9990001800000000000017',
    alias: 'vin002.test',
    creationDate: '2026-08-31T00:00:00',
    balance: 0,
  },
];
const LUCIA: Recipient = {
  accountNumber: 'VIN999',
  cbu: '9990001800000000009991',
  alias: 'lucia.mar.sol',
  holderDisplay: 'Lucía P.',
  bank: 'MindHub Brothers',
  own: false,
};

describe('ScheduleDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  async function render() {
    dialogRef.close.mockReset();
    const data: ScheduleDialogData = { accounts: ACCOUNTS };
    await TestBed.configureTestingModule({
      imports: [ScheduleDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ScheduleDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const press = async (selector: string) => {
      el.querySelector<HTMLButtonElement>(selector)!.click();
      await fixture.whenStable();
    };
    return { fixture, el, press };
  }

  afterEach(() => httpTesting.verify());

  it('looks up the recipient, confirms and schedules a monthly transfer to the resolved account', async () => {
    const { el, fixture, press } = await render();
    typeInto(el, '#scheduleTarget', 'lucia.mar.sol');
    typeInto(el, '#scheduleAmount', '25000');
    typeInto(el, '#scheduleMaxRuns', '12');
    typeInto(el, '#scheduleDescription', 'Alquiler');
    await press('button.submit');

    httpTesting.expectOne((r) => r.url === '/api/accounts/lookup').flush(LUCIA);
    await fixture.whenStable();
    const summary = el.querySelector('[aria-label="Confirmación"]')!;
    expect(summary.textContent).toContain('Lucía P.');
    expect(summary.textContent).toContain('Cada mes');
    expect(summary.textContent).toContain('12 veces');

    await press('button.confirm');
    const req = httpTesting.expectOne(URL);
    expect(req.request.body).toEqual({
      sourceAccountNumber: 'VIN001',
      targetAccountNumber: 'VIN999',
      amount: 25000,
      description: 'Alquiler',
      frequency: 'MONTHLY',
      startDate: bankDate(1),
      maxRuns: 12,
    });
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    req.flush({ id: 1 } as ScheduledTransfer);
    expect(dialogRef.close).toHaveBeenCalledWith({ id: 1 });
  });

  it('asks for the 2FA code when the API needs it and sends it with the same request', async () => {
    const { el, fixture, press } = await render();
    typeInto(el, '#scheduleTarget', 'VIN999');
    typeInto(el, '#scheduleAmount', '100000');
    await press('button.submit');
    httpTesting.expectOne((r) => r.url === '/api/accounts/lookup').flush(LUCIA);
    await fixture.whenStable();

    await press('button.confirm');
    httpTesting
      .expectOne(URL)
      .flush({ secondFactor: 'REQUIRED' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(el.querySelector('#scheduleCode')).not.toBeNull();

    typeInto(el, '#scheduleCode', '123456');
    await press('button.confirm');
    const req = httpTesting.expectOne(URL);
    expect(req.request.body.secondFactorCode).toBe('123456');
    req.flush({ id: 2 } as ScheduledTransfer);
  });

  it('does not look up one of my own accounts', async () => {
    const { el, fixture, press } = await render();
    typeInto(el, '#scheduleTarget', 'vin002');
    typeInto(el, '#scheduleAmount', '10');
    await press('button.submit');
    httpTesting.expectNone((r) => r.url === '/api/accounts/lookup');
    await fixture.whenStable();
    expect(el.querySelector('[aria-label="Confirmación"]')?.textContent).toContain('Cuenta propia');
    await press('button.confirm');
    expect(httpTesting.expectOne(URL).request.body.targetAccountNumber).toBe('VIN002');
  });

  it('validates the form and explains lookup errors without leaving it', async () => {
    const { el, fixture, press } = await render();
    await press('button.submit');
    expect(el.textContent).toContain('Ingresá el CBU, alias o número de cuenta.');

    typeInto(el, '#scheduleTarget', 'nadie.tiene.esto');
    typeInto(el, '#scheduleAmount', '10');
    await press('button.submit');
    httpTesting
      .expectOne((r) => r.url === '/api/accounts/lookup')
      .flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No encontramos una cuenta');
    expect(el.querySelector('[aria-label="Confirmación"]')).toBeNull();
  });
});
