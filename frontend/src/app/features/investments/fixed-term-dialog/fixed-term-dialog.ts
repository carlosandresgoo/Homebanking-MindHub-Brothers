import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { startWith } from 'rxjs';

import { FixedTermService } from '../../../core/api/fixed-term.service';
import { IdempotentOperation } from '../../../core/api/idempotency';
import { Account } from '../../../core/models/account.model';
import {
  CreateFixedTermRequest,
  FIXED_TERM_MIN_AMOUNT,
  FixedTerm,
  FixedTermPlan,
  addDays,
  fixedTermInterest,
} from '../../../core/models/fixed-term.model';

export interface FixedTermDialogData {
  plans: readonly FixedTermPlan[];
  accounts: readonly Account[];
}

function todayIso(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** Simulates and constitutes a fixed term. Closes with the created one, or nothing if cancelled. */
@Component({
  selector: 'app-fixed-term-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    PercentPipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSlideToggleModule,
    ReactiveFormsModule,
  ],
  templateUrl: './fixed-term-dialog.html',
  styleUrl: './fixed-term-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FixedTermDialog {
  private readonly fixedTerms = inject(FixedTermService);
  private readonly dialogRef = inject<MatDialogRef<FixedTermDialog, FixedTerm>>(MatDialogRef);
  protected readonly data = inject<FixedTermDialogData>(MAT_DIALOG_DATA);

  protected readonly minAmount = FIXED_TERM_MIN_AMOUNT;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  /** A retry after a network error never constitutes it twice. */
  private readonly operation = new IdempotentOperation();

  protected readonly form = inject(NonNullableFormBuilder).group({
    accountNumber: [this.defaultAccount(), Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(FIXED_TERM_MIN_AMOUNT)]],
    termDays: [this.data.plans[0]?.termDays ?? 30, Validators.required],
    autoRenew: [false],
  });

  private readonly value = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { requireSync: true },
  );

  protected readonly account = computed(() =>
    this.data.accounts.find((a) => a.number === this.value().accountNumber),
  );
  protected readonly plan = computed(() =>
    this.data.plans.find((p) => p.termDays === this.value().termDays),
  );

  /** Live preview; the API computes the same figure. */
  protected readonly simulation = computed(() => {
    const plan = this.plan();
    const amount = this.value().amount ?? 0;
    if (!plan || amount < FIXED_TERM_MIN_AMOUNT) return null;
    const interest = fixedTermInterest(amount, plan.annualRate, plan.termDays);
    return {
      interest,
      total: amount + interest,
      maturity: addDays(todayIso(), plan.termDays),
    };
  });

  constructor() {
    // The amount can never exceed the chosen account's balance.
    this.form.controls.amount.addValidators((control) =>
      ((control.value as number | null) ?? 0) > (this.account()?.balance ?? 0)
        ? { funds: true }
        : null,
    );
  }

  protected submit(): void {
    this.form.controls.amount.updateValueAndValidity(); // the account may have just changed
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: CreateFixedTermRequest = {
      accountNumber: v.accountNumber,
      amount: v.amount ?? 0,
      termDays: v.termDays,
      autoRenew: v.autoRenew,
    };
    this.submitting.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.fixedTerms.create(request, this.operation.keyFor(request)).subscribe({
      next: (created) => this.dialogRef.close(created),
      error: (err: unknown) => {
        this.operation.settleUnlessUnknown(err);
        this.submitting.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(messageFor(err));
      },
    });
  }

  private defaultAccount(): string {
    const accounts = this.data.accounts;
    const richest = [...accounts].sort((a, b) => b.balance - a.balance)[0];
    return richest?.number ?? '';
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.error?.code === 'BELOW_MINIMUM') return 'El monto está por debajo del mínimo.';
    if (err.status === 422) {
      return err.error?.detail === 'Insufficient funds'
        ? 'No tenés saldo suficiente en esa cuenta.'
        : 'Ese plazo no está disponible. Elegí otro.';
    }
    if (err.status === 404) return 'No encontramos la cuenta elegida.';
  }
  return 'No pudimos constituir el plazo fijo. Intentá de nuevo.';
}
