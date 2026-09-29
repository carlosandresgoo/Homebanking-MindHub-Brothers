import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, startWith, switchMap } from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { TransferService } from '../../core/api/transfer.service';
import { Account } from '../../core/models/account.model';
import { TransferReceipt, TransferRequest } from '../../core/models/transfer.model';
import { toLoadState } from '../../core/utils/load-state';

type Step = 'form' | 'confirm' | 'done';
type Destination = 'own' | 'third';

/** Positive amount with at most two decimals. */
const amountFormat: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value as number | null;
  if (value === null || value === undefined || Number.isNaN(value)) return null;
  if (value < 0.01) return { min: true };
  return Math.round(value * 100) / 100 === value ? null : { decimals: true };
};

@Component({
  selector: 'app-transfers',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './transfers.html',
  styleUrl: './transfers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Transfers {
  private readonly accountService = inject(AccountService);
  private readonly transferService = inject(TransferService);

  /** Optional `?from=<accountId>` to preselect the source account. */
  readonly from = input<string>();

  protected readonly step = signal<Step>('form');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly receipt = signal<TransferReceipt | null>(null);

  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly accountsState = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.accountService.getMyAccounts()))),
    { requireSync: true },
  );
  protected readonly accounts = computed(() => {
    const s = this.accountsState();
    return s.status === 'loaded' ? s.data : [];
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    source: ['', Validators.required],
    destination: ['third' as Destination],
    ownTarget: [''],
    thirdTarget: ['', [Validators.maxLength(20)]],
    amount: [null as number | null, [Validators.required, amountFormat]],
    description: ['', [Validators.maxLength(100)]],
  });

  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { requireSync: true },
  );

  protected readonly sourceAccount = computed<Account | undefined>(() =>
    this.accounts().find((a) => a.number === this.formValue().source),
  );

  /** Own accounts other than the selected source. */
  protected readonly ownTargets = computed(() =>
    this.accounts().filter((a) => a.number !== this.formValue().source),
  );

  protected readonly targetNumber = computed(() => {
    const v = this.formValue();
    return (v.destination === 'own' ? v.ownTarget : v.thirdTarget)?.trim().toUpperCase() ?? '';
  });

  constructor() {
    this.setDestination('third');
    // Preselect the source: ?from=<id>, else the first account with money.
    effect(() => {
      const accounts = this.accounts();
      if (accounts.length === 0 || this.form.controls.source.value) return;
      const fromId = Number(this.from());
      const preferred =
        accounts.find((a) => a.id === fromId) ?? accounts.find((a) => a.balance > 0) ?? accounts[0];
      this.form.controls.source.setValue(preferred.number);
    });
    // Keep the amount validator in sync with the selected account's balance.
    effect(() => {
      const balance = this.sourceAccount()?.balance ?? 0;
      const amount = this.form.controls.amount;
      amount.setValidators([
        Validators.required,
        amountFormat,
        (control) => (((control.value as number | null) ?? 0) > balance ? { funds: true } : null),
      ]);
      amount.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected setDestination(destination: Destination): void {
    this.form.controls.destination.setValue(destination);
    const own = this.form.controls.ownTarget;
    const third = this.form.controls.thirdTarget;
    own.setValidators(destination === 'own' ? [Validators.required] : []);
    third.setValidators(
      destination === 'third' ? [Validators.required, Validators.maxLength(20)] : [],
    );
    own.updateValueAndValidity();
    third.updateValueAndValidity();
  }

  protected useWholeBalance(): void {
    const balance = this.sourceAccount()?.balance;
    if (balance) {
      this.form.controls.amount.setValue(balance);
    }
  }

  protected review(): void {
    if (this.targetNumber() && this.targetNumber() === this.form.controls.source.value) {
      this.form.controls.thirdTarget.setErrors({ same: true });
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.step.set('confirm');
  }

  protected back(): void {
    this.step.set('form');
  }

  protected confirm(): void {
    const v = this.form.getRawValue();
    const request: TransferRequest = {
      sourceAccountNumber: v.source,
      targetAccountNumber: this.targetNumber(),
      amount: v.amount ?? 0,
      description: v.description.trim() || undefined,
    };
    this.submitting.set(true);
    this.transferService.transfer(request).subscribe({
      next: (receipt) => {
        this.submitting.set(false);
        this.receipt.set(receipt);
        this.step.set('done');
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(messageFor(err));
        this.step.set('form');
      },
    });
  }

  /** Back to an empty form with fresh balances (the source is preselected again by the effect). */
  protected newTransfer(): void {
    this.receipt.set(null);
    this.error.set(null);
    this.form.reset({ destination: 'third' });
    this.setDestination('third');
    this.step.set('form');
    this.reload$.next();
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 422) {
      return err.error?.detail === 'Insufficient funds'
        ? 'No tenés saldo suficiente en la cuenta de origen.'
        : 'No podés transferir a la misma cuenta.';
    }
    if (err.status === 404) return 'No encontramos la cuenta destino. Revisá el número.';
    if (err.status === 400) return 'Revisá los datos de la transferencia.';
  }
  return 'No pudimos hacer la transferencia. Intentá de nuevo más tarde.';
}
