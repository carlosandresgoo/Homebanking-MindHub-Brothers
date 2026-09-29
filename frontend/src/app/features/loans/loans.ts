import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, filter, forkJoin, switchMap } from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { LoanService } from '../../core/api/loan.service';
import { ClientLoan, LOAN_ICON, Loan } from '../../core/models/loan.model';
import { toLoadState } from '../../core/utils/load-state';
import { ApplyLoanDialog, ApplyLoanDialogData } from './apply-loan-dialog/apply-loan-dialog';
import {
  PayInstallmentDialog,
  PayInstallmentDialogData,
} from './pay-installment-dialog/pay-installment-dialog';

@Component({
  selector: 'app-loans',
  imports: [
    CurrencyPipe,
    DatePipe,
    PercentPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './loans.html',
  styleUrl: './loans.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Loans {
  private readonly loanService = inject(LoanService);
  private readonly accountService = inject(AccountService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly icon = LOAN_ICON;

  protected readonly state = toSignal(
    this.reload$.pipe(
      switchMap(() =>
        toLoadState(
          forkJoin({
            catalog: this.loanService.getCatalog(),
            loans: this.loanService.getMyLoans(),
            accounts: this.accountService.getMyAccounts(),
          }),
        ),
      ),
    ),
    { requireSync: true },
  );

  /** Products with an unpaid loan. */
  protected readonly activeLoanIds = computed(() => {
    const s = this.state();
    return s.status === 'loaded' ? s.data.loans.filter((l) => !l.paidOff).map((l) => l.loanId) : [];
  });

  protected isActive(loan: Loan): boolean {
    return this.activeLoanIds().includes(loan.id);
  }

  protected progress(loan: ClientLoan): number {
    return Math.round((loan.paymentsMade / loan.payments) * 100);
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected apply(preselectedLoanId?: number): void {
    const s = this.state();
    if (s.status !== 'loaded') return;
    const data: ApplyLoanDialogData = {
      catalog: s.data.catalog,
      accounts: s.data.accounts,
      activeLoanIds: this.activeLoanIds(),
      preselectedLoanId,
    };
    this.dialog
      .open<ApplyLoanDialog, ApplyLoanDialogData, ClientLoan>(ApplyLoanDialog, {
        data,
        width: '560px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((created): created is ClientLoan => !!created))
      .subscribe((created) => {
        this.snackBar.open(
          `¡Préstamo ${created.name} aprobado! Acreditamos el dinero en tu cuenta.`,
          'OK',
        );
        this.reload$.next();
      });
  }

  protected pay(loan: ClientLoan): void {
    const s = this.state();
    if (s.status !== 'loaded') return;
    this.dialog
      .open<PayInstallmentDialog, PayInstallmentDialogData, ClientLoan>(PayInstallmentDialog, {
        data: { loan, accounts: s.data.accounts },
        width: '460px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((updated): updated is ClientLoan => !!updated))
      .subscribe((updated) => {
        this.snackBar.open(
          updated.paidOff
            ? `¡Terminaste de pagar tu préstamo ${updated.name}!`
            : `Pagaste la cuota ${updated.paymentsMade} de ${updated.payments}.`,
          'OK',
        );
        this.reload$.next();
      });
  }
}
