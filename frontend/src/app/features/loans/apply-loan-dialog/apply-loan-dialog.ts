import { CurrencyPipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { startWith } from 'rxjs';

import { IdempotentOperation } from '../../../core/api/idempotency';
import { LoanService } from '../../../core/api/loan.service';
import { Account } from '../../../core/models/account.model';
import {
  ClientLoan,
  LOAN_ICON,
  Loan,
  LoanApplication,
  totalWithInterest,
} from '../../../core/models/loan.model';

export interface ApplyLoanDialogData {
  catalog: readonly Loan[];
  accounts: readonly Account[];
  /** Products with an unpaid loan cannot be requested again. */
  activeLoanIds: readonly number[];
  preselectedLoanId?: number;
}

/** Closes with the created loan, or nothing if cancelled. */
@Component({
  selector: 'app-apply-loan-dialog',
  imports: [
    CurrencyPipe,
    PercentPipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './apply-loan-dialog.html',
  styleUrl: './apply-loan-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ApplyLoanDialog {
  private readonly loanService = inject(LoanService);
  private readonly dialogRef = inject<MatDialogRef<ApplyLoanDialog, ClientLoan>>(MatDialogRef);
  protected readonly data = inject<ApplyLoanDialogData>(MAT_DIALOG_DATA);

  protected readonly icon = LOAN_ICON;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Resubmitting the same application after a network error never creates two loans. */
  private readonly operation = new IdempotentOperation();

  protected readonly form = inject(NonNullableFormBuilder).group({
    loanId: [this.firstAvailableId(), Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(1)]],
    payments: [null as number | null, Validators.required],
    accountNumber: [this.data.accounts[0]?.number ?? '', Validators.required],
  });

  private readonly value = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { requireSync: true },
  );

  protected readonly loan = computed(() =>
    this.data.catalog.find((l) => l.id === this.value().loanId),
  );

  protected readonly total = computed(() => {
    const loan = this.loan();
    const amount = this.value().amount;
    return loan && amount && amount > 0 ? totalWithInterest(amount, loan.interestRate) : null;
  });

  protected readonly installment = computed(() => {
    const total = this.total();
    const payments = this.value().payments;
    return total !== null && payments ? Math.round((total / payments) * 100) / 100 : null;
  });

  constructor() {
    // Product change: reset installments and bound the amount by the product's maximum.
    effect(() => {
      const loan = this.loan();
      if (!loan) return;
      const amount = this.form.controls.amount;
      amount.setValidators([
        Validators.required,
        Validators.min(1),
        Validators.max(loan.maxAmount),
      ]);
      amount.updateValueAndValidity({ emitEvent: false });
      const payments = this.form.controls.payments;
      if (payments.value !== null && !loan.payments.includes(payments.value)) {
        payments.setValue(null);
      }
    });
  }

  protected isActive(loan: Loan): boolean {
    return this.data.activeLoanIds.includes(loan.id);
  }

  protected submit(): void {
    if (this.form.invalid || !this.loan() || this.isActive(this.loan()!)) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    const application: LoanApplication = {
      loanId: v.loanId,
      amount: v.amount ?? 0,
      payments: v.payments ?? 0,
      accountNumber: v.accountNumber,
    };
    this.loanService.apply(application, this.operation.keyFor(application)).subscribe({
      next: (created) => this.dialogRef.close(created),
      error: (err: unknown) => {
        this.operation.settleUnlessUnknown(err);
        this.submitting.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(messageFor(err));
      },
    });
  }

  private firstAvailableId(): number {
    const { catalog, activeLoanIds, preselectedLoanId } = this.data;
    if (preselectedLoanId && !activeLoanIds.includes(preselectedLoanId)) return preselectedLoanId;
    return (catalog.find((l) => !activeLoanIds.includes(l.id)) ?? catalog[0]).id;
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 409) return 'Ya tenés un préstamo activo de este tipo.';
    if (err.status === 422) return 'El monto o las cuotas no están disponibles para este préstamo.';
    if (err.status === 404) return 'No encontramos la cuenta elegida.';
    if (err.status === 400) return 'Revisá los datos de la solicitud.';
  }
  return 'No pudimos procesar la solicitud. Intentá de nuevo más tarde.';
}
