import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { LoanService } from '../../../core/api/loan.service';
import { Account } from '../../../core/models/account.model';
import { ClientLoan } from '../../../core/models/loan.model';

export interface PayInstallmentDialogData {
  loan: ClientLoan;
  accounts: readonly Account[];
}

/** Pays the next installment. Closes with the updated loan, or nothing if cancelled. */
@Component({
  selector: 'app-pay-installment-dialog',
  imports: [
    CurrencyPipe,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title class="title">
      <mat-icon>payments</mat-icon>
      Pagar cuota
    </h2>
    <mat-dialog-content>
      @if (error(); as message) {
        <div class="error-banner" role="alert">
          <mat-icon>error</mat-icon>
          <span>{{ message }}</span>
        </div>
      }
      <div class="installment">
        <span>
          Cuota {{ data.loan.paymentsMade + 1 }} de {{ data.loan.payments }} · Préstamo
          {{ data.loan.name }}
        </span>
        <strong class="hb-tabular">{{ data.loan.nextInstallment | currency }}</strong>
      </div>
      <mat-form-field>
        <mat-label>Pagar desde</mat-label>
        <mat-icon matPrefix>account_balance_wallet</mat-icon>
        <mat-select [formControl]="account" id="payAccount">
          @for (a of data.accounts; track a.id) {
            <mat-option [value]="a.number" [disabled]="a.balance < data.loan.nextInstallment">
              {{ a.number }} · {{ a.balance | currency }}
            </mat-option>
          }
        </mat-select>
        <mat-error>Elegí una cuenta con saldo suficiente.</mat-error>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close [disabled]="submitting()">Cancelar</button>
      <button
        mat-flat-button
        type="button"
        class="submit"
        (click)="pay()"
        [disabled]="submitting()"
      >
        @if (submitting()) {
          <mat-spinner diameter="18" aria-label="Pagando" />
        } @else {
          Pagar {{ data.loan.nextInstallment | currency }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .title {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .title mat-icon {
      color: var(--mat-sys-primary);
    }
    mat-dialog-content {
      display: grid;
      gap: 16px;
    }
    .installment {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 16px;
      border-radius: 14px;
      background: var(--mat-sys-surface-container);
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
    .installment strong {
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-headline-small);
      font-weight: 700;
    }
    mat-icon[matPrefix] {
      margin: 0 4px 0 12px;
      color: var(--mat-sys-on-surface-variant);
    }
    .error-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      font: var(--mat-sys-body-medium);
    }
    mat-dialog-actions {
      padding: 8px 24px 20px;
    }
    .submit {
      min-width: 160px;
    }
    mat-spinner {
      --mat-progress-spinner-active-indicator-color: currentColor;
      margin: 0 auto;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PayInstallmentDialog {
  private readonly loanService = inject(LoanService);
  private readonly dialogRef = inject<MatDialogRef<PayInstallmentDialog, ClientLoan>>(MatDialogRef);
  protected readonly data = inject<PayInstallmentDialogData>(MAT_DIALOG_DATA);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly account = new FormControl(
    this.data.accounts.find((a) => a.balance >= this.data.loan.nextInstallment)?.number ?? '',
    { nonNullable: true, validators: Validators.required },
  );

  protected pay(): void {
    if (this.account.invalid) {
      this.account.markAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.loanService.payInstallment(this.data.loan.id, this.account.value).subscribe({
      next: (updated) => this.dialogRef.close(updated),
      error: (err: unknown) => {
        this.submitting.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 422
            ? 'No tenés saldo suficiente en esa cuenta.'
            : 'No pudimos registrar el pago. Intentá de nuevo.',
        );
      },
    });
  }
}
