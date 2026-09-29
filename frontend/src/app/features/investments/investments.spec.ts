import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSlideToggleHarness } from '@angular/material/slide-toggle/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { FixedTerm } from '../../core/models/fixed-term.model';
import { provideTestDefaults } from '../../testing/providers';
import { FixedTermDialog } from './fixed-term-dialog/fixed-term-dialog';
import { Investments } from './investments';

const URL = '/api/clients/current/fixed-terms';
const ACTIVE: FixedTerm = {
  id: 2,
  accountId: 2,
  accountNumber: 'VIN002',
  principal: 10000,
  annualRate: 0.37,
  termDays: 90,
  interest: 912.33,
  total: 10912.33,
  startDate: '2026-09-24',
  maturityDate: '2026-12-23',
  autoRenew: true,
  status: 'ACTIVE',
  paidAt: null,
};
const PAID: FixedTerm = {
  ...ACTIVE,
  id: 1,
  accountNumber: 'VIN001',
  principal: 3000,
  annualRate: 0.35,
  termDays: 30,
  interest: 86.3,
  total: 3086.3,
  autoRenew: false,
  status: 'PAID',
  paidAt: '2026-09-29T00:05:00',
};

describe('Investments', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Investments],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(terms: FixedTerm[]) {
    const fixture = TestBed.createComponent(Investments);
    fixture.detectChanges();
    httpTesting.expectOne(URL).flush(terms);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('invites to simulate the first one', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('Todavía no tenés plazos fijos');
  });

  it('shows totals, active fixed terms with their maturity and the paid history', async () => {
    const { el } = await render([ACTIVE, PAID]);
    expect(el.querySelector('.totals')?.textContent).toMatch(/Invertido\s*\$\s*10\.000,00/);
    expect(el.querySelector('.totals')?.textContent).toMatch(/a cobrar\s*\+\s*\$\s*912,33/);
    expect(el.querySelector('.totals')?.textContent).toMatch(/cobrados\s*\$\s*86,30/);

    const active = el.querySelectorAll('li.term');
    expect(active).toHaveLength(1);
    expect(active[0].textContent).toContain('90 días · TNA 37');
    expect(active[0].textContent).toContain('Vence el 23 de diciembre');
    expect(active[0].textContent).toMatch(/Cobrás\s*\$\s*10\.912,33/);
    expect(el.querySelectorAll('.history li')).toHaveLength(1);
  });

  it('turns automatic renewal off', async () => {
    const { fixture } = await render([ACTIVE]);
    const toggle =
      await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSlideToggleHarness);
    await toggle.uncheck();
    const req = httpTesting.expectOne(`${URL}/2`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ autoRenew: false });
    req.flush({ ...ACTIVE, autoRenew: false });
  });

  it('loads rates and balances, then opens the simulator and reloads after constituting', async () => {
    const open = vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
      afterClosed: () => of(ACTIVE),
    } as ReturnType<MatDialog['open']>);
    const { fixture, el } = await render([]);

    el.querySelector<HTMLButtonElement>('.new-button')!.click();
    httpTesting.expectOne('/api/fixed-terms/plans').flush([{ termDays: 30, annualRate: 0.35 }]);
    httpTesting.expectOne('/api/clients/current/accounts').flush([]);
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(FixedTermDialog, expect.anything());
    httpTesting.expectOne(URL).flush([ACTIVE]);
  });
});
