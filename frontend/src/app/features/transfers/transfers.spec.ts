import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';
import { MatSelectHarness } from '@angular/material/select/testing';

import { Account } from '../../core/models/account.model';
import { TransferReceipt } from '../../core/models/transfer.model';
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

  async function render(from?: string) {
    const fixture = TestBed.createComponent(Transfers);
    if (from) fixture.componentRef.setInput('from', from);
    fixture.detectChanges();
    httpTesting.expectOne('/api/clients/current/accounts').flush(ACCOUNTS);
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
    expect(receipt.querySelector('a')?.getAttribute('href')).toBe('/accounts/11');
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
});
