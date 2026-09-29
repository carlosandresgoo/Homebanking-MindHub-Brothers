import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { switchMap } from 'rxjs';

import { SummaryService } from '../../../core/api/summary.service';
import { CATEGORY_LABEL, TransactionCategory } from '../../../core/models/account.model';
import { toLoadState } from '../../../core/utils/load-state';
import { BarChart, BarSeries } from '../../../shared/charts/bar-chart';
import { DonutChart } from '../../../shared/charts/donut-chart';
import { LineChart } from '../../../shared/charts/line-chart';

/** Stable colour per category (theme tokens, so they follow light/dark mode). */
const CATEGORY_COLOR: Record<TransactionCategory, string> = {
  TRANSFER_OUT: 'var(--mat-sys-primary)',
  LOAN_PAYMENT: 'var(--mat-sys-tertiary)',
  OTHER: 'var(--mat-sys-secondary)',
  DEPOSIT: 'var(--hb-positive)',
  TRANSFER_IN: 'var(--mat-sys-outline)',
  LOAN_DISBURSEMENT: 'var(--mat-sys-error)',
  // Never spending (see the backend summary), listed for completeness.
  FIXED_TERM_DEPOSIT: 'var(--mat-sys-outline)',
  FIXED_TERM_PAYOUT: 'var(--mat-sys-outline)',
  FIXED_TERM_INTEREST: 'var(--hb-positive)',
};

const SERIES: BarSeries[] = [
  { name: 'Ingresos', color: 'var(--hb-positive)' },
  { name: 'Egresos', color: 'var(--mat-sys-error)' },
];

const monthName = new Intl.DateTimeFormat('es-AR', { month: 'short' });

/** Dashboard charts: income vs expenses, spending by category and the balance over time. */
@Component({
  selector: 'app-insights',
  imports: [BarChart, CurrencyPipe, DonutChart, LineChart, MatButtonToggleModule, MatIconModule],
  templateUrl: './insights.html',
  styleUrl: './insights.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Insights {
  private readonly summaryService = inject(SummaryService);

  protected readonly periods = [3, 6, 12];
  protected readonly months = signal(6);
  protected readonly series = SERIES;

  protected readonly state = toSignal(
    toObservable(this.months).pipe(
      switchMap((months) => toLoadState(this.summaryService.getSummary(months))),
    ),
    { initialValue: { status: 'loading' as const } },
  );

  private readonly summary = computed(() => {
    const s = this.state();
    return s.status === 'loaded' ? s.data : null;
  });

  protected readonly monthGroups = computed(() =>
    (this.summary()?.months ?? []).map((m) => {
      const [y, mo] = m.month.split('-').map(Number);
      return {
        label: monthName.format(new Date(y, mo - 1, 1)).replace('.', ''),
        values: [m.income, m.expense],
      };
    }),
  );

  protected readonly totals = computed(() =>
    (this.summary()?.months ?? []).reduce(
      (acc, m) => ({ income: acc.income + m.income, expense: acc.expense + m.expense }),
      { income: 0, expense: 0 },
    ),
  );

  protected readonly slices = computed(() =>
    (this.summary()?.expenses ?? []).map((e) => ({
      label: CATEGORY_LABEL[e.category] ?? e.category,
      value: e.amount,
      color: CATEGORY_COLOR[e.category] ?? 'var(--mat-sys-outline)',
    })),
  );

  protected readonly balancePoints = computed(() =>
    (this.summary()?.balanceHistory ?? []).map((p) => ({ date: p.date, value: p.balance })),
  );

  protected setMonths(months: number): void {
    this.months.set(months);
  }
}
