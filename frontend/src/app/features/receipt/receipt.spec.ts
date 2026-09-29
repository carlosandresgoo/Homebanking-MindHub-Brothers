import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MovementReceipt } from '../../core/models/account.model';
import { provideTestDefaults } from '../../testing/providers';
import { Receipt } from './receipt';

const TRANSFER: MovementReceipt = {
  id: 42,
  accountId: 7,
  accountNumber: 'VIN001',
  accountHolder: 'Melba Morel',
  type: 'DEBIT',
  category: 'TRANSFER_OUT',
  amount: 1500,
  description: 'Transferencia a VIN-27905812 · Alquiler',
  date: '2026-09-29T15:04:00',
  balanceAfter: 3500,
  counterparty: 'VIN-27905812',
  counterpartyHolder: 'Lucía P.',
};

describe('Receipt', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Receipt],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(receipt: MovementReceipt | null) {
    const fixture = TestBed.createComponent(Receipt);
    fixture.componentRef.setInput('id', '42');
    fixture.detectChanges();
    const req = httpTesting.expectOne('/api/transactions/42');
    if (receipt) req.flush(receipt);
    else req.flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows every detail of a transfer, including the other side', async () => {
    const { el } = await render(TRANSFER);
    const text = el.querySelector('.receipt')?.textContent ?? '';
    expect(text).toContain('N.º 42');
    expect(text).toMatch(/−\s*\$\s*1\.500,00/);
    expect(text).toContain('Transferencia enviada');
    expect(text).toContain('Melba Morel');
    expect(text).toContain('Cuenta de destino');
    expect(text).toContain('VIN-27905812');
    expect(text).toContain('Lucía P.');
    expect(text).toContain('29 de septiembre de 2026, 15:04');
    expect(el.querySelector('a[href="/accounts/7"]')).not.toBeNull();
  });

  it('prints (or saves as PDF) with the browser', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    const { el } = await render(TRANSFER);
    el.querySelector<HTMLButtonElement>('button.print')!.click();
    expect(print).toHaveBeenCalled();
    print.mockRestore();
  });

  it('omits the other side for movements that are not transfers', async () => {
    const { el } = await render({
      ...TRANSFER,
      type: 'CREDIT',
      category: 'DEPOSIT',
      counterparty: null,
      counterpartyHolder: null,
    });
    expect(el.textContent).not.toContain('Cuenta de');
    expect(el.textContent).toContain('Ingreso');
  });

  it('explains a movement that is not mine', async () => {
    const { el } = await render(null);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'No encontramos este movimiento',
    );
  });
});
