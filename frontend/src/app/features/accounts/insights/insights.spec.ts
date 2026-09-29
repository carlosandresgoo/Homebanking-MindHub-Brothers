import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatButtonToggleHarness } from '@angular/material/button-toggle/testing';

import { FinancialSummary } from '../../../core/models/summary.model';
import { provideTestDefaults } from '../../../testing/providers';
import { Insights } from './insights';

const SUMMARY: FinancialSummary = {
  totalBalance: 36500,
  months: [
    { month: '2026-08-01', income: 37000, expense: 0 },
    { month: '2026-09-01', income: 3000, expense: 7500 },
  ],
  expenses: [
    { category: 'LOAN_PAYMENT', amount: 6000 },
    { category: 'OTHER', amount: 1500 },
  ],
  balanceHistory: [
    { date: '2026-09-27', balance: 40000 },
    { date: '2026-09-28', balance: 38000 },
    { date: '2026-09-29', balance: 36500 },
  ],
};

const isSummary =
  (months: string) => (r: { url: string; params: { get(k: string): string | null } }) =>
    r.url === '/api/clients/current/summary' && r.params.get('months') === months;

describe('Insights', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Insights],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(summary: FinancialSummary = SUMMARY) {
    const fixture = TestBed.createComponent(Insights);
    fixture.detectChanges();
    httpTesting.expectOne(isSummary('6')).flush(summary);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('draws income and expenses per month with the period totals', async () => {
    const { el } = await render();
    // Two months × two series.
    expect(el.querySelectorAll('app-bar-chart rect')).toHaveLength(4);
    expect(el.querySelector('app-bar-chart')?.textContent).toContain('ago');
    expect(el.querySelector('.totals .income')?.textContent).toMatch(/40\.000,00/);
    expect(el.querySelector('.totals .expense')?.textContent).toMatch(/7\.500,00/);
  });

  it('lists spending by category with its share', async () => {
    const { el } = await render();
    const items = el.querySelectorAll('app-donut-chart li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Cuota de préstamo');
    expect(items[0].textContent).toMatch(/80\s*%/);
    expect(el.querySelectorAll('app-donut-chart circle')).toHaveLength(3); // track + 2 arcs
  });

  it('draws the balance line and describes it for screen readers', async () => {
    const { el } = await render();
    expect(el.querySelector('app-line-chart path.line')?.getAttribute('d')).toMatch(/^M.*L.*L/);
    expect(el.querySelector('app-line-chart .hb-visually-hidden')?.textContent).toMatch(
      /40\.000,00.*36\.500,00/s,
    );
  });

  it('says when there was no spending', async () => {
    const { el } = await render({ ...SUMMARY, expenses: [] });
    expect(el.textContent).toContain('Sin gastos en este período.');
  });

  it('reloads for another period', async () => {
    const { fixture } = await render();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const twelve = await loader.getHarness(MatButtonToggleHarness.with({ text: '12 meses' }));
    await twelve.check();
    httpTesting.expectOne(isSummary('12')).flush(SUMMARY);
  });
});
