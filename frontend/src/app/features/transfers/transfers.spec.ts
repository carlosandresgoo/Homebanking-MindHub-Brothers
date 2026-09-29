import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';
import { MatSelectHarness } from '@angular/material/select/testing';

import { MatAutocompleteHarness } from '@angular/material/autocomplete/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Account } from '../../core/models/account.model';
import { Contact } from '../../core/models/contact.model';
import { TransferLimits, TransferReceipt } from '../../core/models/transfer.model';
import { provideTestDefaults, typeInto } from '../../testing/providers';
import { Transfers } from './transfers';

const ACCOUNTS: Account[] = [
  { id: 10, number: 'VIN-EMPTY', creationDate: '2026-09-01T00:00:00', balance: 0 },
  { id: 11, number: 'VIN001', creationDate: '2026-08-30T00:00:00', balance: 5000 },
  { id: 12, number: 'VIN002', creationDate: '2026-08-31T00:00:00', balance: 7500 },
];

const RECEIPT: TransferReceipt = {
  transactionId: 99,
  sourceAccountId: 11,
  sourceAccountNumber: 'VIN001',
  targetAccountNumber: 'VIN999',
  amount: 100,
  description: 'Transferencia a VIN999 · Cena',
  date: '2026-09-29T12:00:00',
  sourceBalanceAfter: 4900,
};

const LUCIA: Contact = {
  id: 1,
  alias: 'Lucía',
  accountNumber: 'VIN999',
  holderDisplay: 'Lucía P.',
  createdAt: '2026-09-01T10:00:00',
};

const LIMITS: TransferLimits = {
  dailyLimit: 200000,
  usedToday: 0,
  remainingToday: 200000,
  secondFactorEnabled: false,
  secondFactorThreshold: 50000,
  limitWithSecondFactor: 1000000,
};

describe('Transfers', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Transfers],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(
    from?: string,
    limits: TransferLimits = LIMITS,
    contacts: Contact[] = [],
    to?: string,
  ) {
    const fixture = TestBed.createComponent(Transfers);
    if (from) fixture.componentRef.setInput('from', from);
    if (to) fixture.componentRef.setInput('to', to);
    fixture.detectChanges();
    httpTesting.expectOne('/api/clients/current/accounts').flush(ACCOUNTS);
    httpTesting.expectOne('/api/transfers/limits').flush(limits);
    httpTesting.expectOne('/api/clients/current/contacts').flush(contacts);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  async function click(fixture: ComponentFixture<Transfers>, text: string) {
    const el = fixture.nativeElement as HTMLElement;
    Array.from(el.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes(text))!
      .click();
    await fixture.whenStable();
  }

  it('preselects the first account with money and shows what is available', async () => {
    const { fixture, el } = await render();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const source = await loader.getHarness(MatSelectHarness.with({ selector: '#source' }));

    expect(await source.getValueText()).toContain('VIN001');
    expect(el.textContent).toMatch(/Disponible: \$\s*5\.000,00/);
  });

  it('preselects the account given in ?from', async () => {
    const { fixture } = await render('12');
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const source = await loader.getHarness(MatSelectHarness.with({ selector: '#source' }));
    expect(await source.getValueText()).toContain('VIN002');
  });

  it('does not continue when the amount exceeds the balance', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '5000.01');
    await click(fixture, 'Continuar');

    expect(el.textContent).toContain('Supera tu saldo disponible.');
    expect(el.querySelector('[aria-label="Confirmación"]')).toBeNull();
  });

  it('requires a destination and rejects sending to the same account', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#amount', '10');
    await click(fixture, 'Continuar');
    expect(el.textContent).toContain('Ingresá el número de cuenta.');

    typeInto(el, '#thirdTarget', 'vin001');
    await click(fixture, 'Continuar');
    expect(el.textContent).toContain('No podés transferir a la misma cuenta.');
  });

  it('reviews, confirms and shows the receipt for a third-party transfer', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#thirdTarget', 'vin999');
    typeInto(el, '#amount', '100');
    typeInto(el, '#description', 'Cena');
    await click(fixture, 'Continuar');

    const summary = el.querySelector('[aria-label="Confirmación"]')!;
    expect(summary.textContent).toContain('VIN999');
    expect(summary.textContent).toMatch(/\$\s*100,00/);
    expect(summary.textContent).toMatch(/Saldo después\s*\$\s*4\.900,00/);

    await click(fixture, 'Confirmar transferencia');
    const req = httpTesting.expectOne('/api/transfers');
    expect(req.request.body).toEqual({
      sourceAccountNumber: 'VIN001',
      targetAccountNumber: 'VIN999',
      amount: 100,
      description: 'Cena',
    });
    req.flush(RECEIPT);
    await fixture.whenStable();

    const receipt = el.querySelector('[aria-label="Comprobante"]')!;
    expect(receipt.textContent).toContain('¡Transferencia realizada!');
    expect(receipt.textContent).toMatch(/Nuevo saldo\s*\$\s*4\.900,00/);
    expect(receipt.querySelector('a.receipt-link')?.getAttribute('href')).toBe('/movements/99');
    expect(receipt.querySelector('a[href="/accounts/11"]')).not.toBeNull();
  });

  it('transfers between my own accounts', async () => {
    const { fixture, el } = await render();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const own = await loader.getHarness(MatButtonToggleHarness.with({ text: /Mis cuentas/ }));
    await own.check();
    const target = await loader.getHarness(MatSelectHarness.with({ selector: '#ownTarget' }));
    await target.open();
    const options = await target.getOptions();
    // The source account is not offered as destination.
    expect(await Promise.all(options.map((o) => o.getText()))).not.toContain(
      expect.stringContaining('VIN001'),
    );
    await target.clickOptions({ text: /VIN002/ });
    typeInto(el, '#amount', '50');
    await click(fixture, 'Continuar');
    expect(el.textContent).toContain('Cuenta propia');

    await click(fixture, 'Confirmar transferencia');
    const req = httpTesting.expectOne('/api/transfers');
    expect(req.request.body.targetAccountNumber).toBe('VIN002');
    req.flush({ ...RECEIPT, targetAccountNumber: 'VIN002' });
  });

  it('goes back to the form with a clear message when the API refuses', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#thirdTarget', 'VIN-00000000');
    typeInto(el, '#amount', '10');
    await click(fixture, 'Continuar');
    await click(fixture, 'Confirmar transferencia');
    httpTesting.expectOne('/api/transfers').flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'No encontramos la cuenta destino',
    );
    expect(el.querySelector<HTMLInputElement>('#thirdTarget')!.value).toBe('VIN-00000000');
  });

  it('stays on the confirmation and retries with the same Idempotency-Key when the outcome is unknown', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '100');
    await click(fixture, 'Continuar');
    await click(fixture, 'Confirmar transferencia');
    const first = httpTesting.expectOne('/api/transfers');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
    first.flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect(el.querySelector('[aria-label="Confirmación"] [role="alert"]')?.textContent).toContain(
      'no se va a duplicar',
    );
    await click(fixture, 'Reintentar');
    const retry = httpTesting.expectOne('/api/transfers');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(RECEIPT);
    await fixture.whenStable();
    expect(el.textContent).toContain('¡Transferencia realizada!');
  });

  it('picks a saved recipient by alias and shows it in the confirmation', async () => {
    const { fixture, el } = await render(undefined, LIMITS, [LUCIA]);
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const autocomplete = await loader.getHarness(MatAutocompleteHarness);
    await autocomplete.enterText('luc');
    const options = await autocomplete.getOptions();
    expect(await options[0].getText()).toContain('Lucía');
    await autocomplete.selectOption({ text: /Lucía/ });

    expect(el.querySelector<HTMLInputElement>('#thirdTarget')!.value).toBe('VIN999');
    expect(el.textContent).toContain('Lucía · Lucía P.');
    typeInto(el, '#amount', '10');
    await click(fixture, 'Continuar');
    expect(el.querySelector('[aria-label="Confirmación"] .tag')?.textContent).toContain('Lucía');
  });

  it('prefills the destination from ?to=', async () => {
    const { el } = await render(undefined, LIMITS, [LUCIA], 'vin999');
    expect(el.querySelector<HTMLInputElement>('#thirdTarget')!.value).toBe('VIN999');
  });

  it('offers to save a new recipient after transferring', async () => {
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of({ ...LUCIA, accountNumber: 'VIN777', alias: 'Nuevo' }),
    } as ReturnType<MatDialog['open']>);
    const { fixture, el } = await render(undefined, LIMITS, [LUCIA]);
    typeInto(el, '#thirdTarget', 'VIN777');
    typeInto(el, '#amount', '10');
    await click(fixture, 'Continuar');
    await click(fixture, 'Confirmar transferencia');
    httpTesting.expectOne('/api/transfers').flush({ ...RECEIPT, targetAccountNumber: 'VIN777' });
    await fixture.whenStable();

    await click(fixture, 'Guardar en mi agenda');
    expect(open.mock.calls[0][1]?.data).toEqual({ accountNumber: 'VIN777' });
    httpTesting.expectOne('/api/clients/current/contacts').flush([LUCIA]);
  });

  it('does not offer to save a recipient that is already saved', async () => {
    const { fixture, el } = await render(undefined, LIMITS, [LUCIA]);
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '10');
    await click(fixture, 'Continuar');
    await click(fixture, 'Confirmar transferencia');
    httpTesting.expectOne('/api/transfers').flush(RECEIPT);
    await fixture.whenStable();
    expect(el.textContent).not.toContain('Guardar en mi agenda');
  });

  it('checks the daily limit only for transfers to other people', async () => {
    const { fixture, el } = await render(undefined, {
      ...LIMITS,
      usedToday: 199700,
      remainingToday: 300,
    });
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '500');
    await fixture.whenStable();
    expect(el.querySelector('.limit-info')?.textContent).toMatch(/hasta\s*\$\s*300,00/);
    expect(el.querySelector('.limit-info a')?.getAttribute('href')).toBe('/profile');

    await click(fixture, 'Continuar');
    expect(el.textContent).toContain('Supera tu límite diario para transferir a terceros.');
    expect(el.querySelector('[aria-label="Confirmación"]')).toBeNull();

    // Typing one of my own accounts is not a transfer to others: no limit.
    typeInto(el, '#thirdTarget', 'VIN002');
    await click(fixture, 'Continuar');
    expect(el.querySelector('[aria-label="Confirmación"]')).not.toBeNull();
    expect(el.querySelector('.limit-info')).toBeNull();
  });

  it('asks for the authenticator code for large transfers when 2FA is on', async () => {
    const { fixture, el } = await render(undefined, {
      ...LIMITS,
      secondFactorEnabled: true,
      secondFactorThreshold: 1000,
    });
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '1000');
    await click(fixture, 'Continuar');

    expect(el.querySelector('#secondFactorCode')).not.toBeNull();
    await click(fixture, 'Confirmar transferencia');
    httpTesting.expectNone('/api/transfers');
    expect(el.textContent).toContain('Ingresá los 6 dígitos del código.');

    typeInto(el, '#secondFactorCode', '123456');
    await click(fixture, 'Confirmar transferencia');
    const req = httpTesting.expectOne('/api/transfers');
    expect(req.request.body.secondFactorCode).toBe('123456');
    req.flush(RECEIPT);
  });

  it('asks for a code when the API requires one, and for another one when it is wrong', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '100');
    await click(fixture, 'Continuar');
    expect(el.querySelector('#secondFactorCode')).toBeNull();

    await click(fixture, 'Confirmar transferencia');
    httpTesting
      .expectOne('/api/transfers')
      .flush({ secondFactor: 'REQUIRED' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('necesitamos el código');

    typeInto(el, '#secondFactorCode', '111111');
    await click(fixture, 'Confirmar transferencia');
    httpTesting
      .expectOne('/api/transfers')
      .flush({ secondFactor: 'INVALID' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('El código no es correcto');
    expect(el.querySelector<HTMLInputElement>('#secondFactorCode')!.value).toBe('');
    expect(el.querySelector('[aria-label="Confirmación"]')).not.toBeNull();
  });

  it('explains the daily limit refused by the API and refreshes the allowance', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '100');
    await click(fixture, 'Continuar');
    await click(fixture, 'Confirmar transferencia');
    httpTesting
      .expectOne('/api/transfers')
      .flush(
        { code: 'DAILY_LIMIT_EXCEEDED', remaining: 50 },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    httpTesting.expectOne('/api/clients/current/accounts').flush(ACCOUNTS);
    httpTesting.expectOne('/api/transfers/limits').flush({ ...LIMITS, remainingToday: 50 });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toMatch(
      /Supera tu límite diario.*hasta \$\s*50,00/,
    );
  });

  it('uses a new Idempotency-Key after a definitive answer', async () => {
    const { fixture, el } = await render();
    typeInto(el, '#thirdTarget', 'VIN999');
    typeInto(el, '#amount', '10');
    await click(fixture, 'Continuar');
    await click(fixture, 'Confirmar transferencia');
    const first = httpTesting.expectOne('/api/transfers');
    first.flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    await click(fixture, 'Continuar');
    await click(fixture, 'Confirmar transferencia');
    const second = httpTesting.expectOne('/api/transfers');
    expect(second.request.headers.get('Idempotency-Key')).not.toBe(
      first.request.headers.get('Idempotency-Key'),
    );
    second.flush(RECEIPT);
  });
});
