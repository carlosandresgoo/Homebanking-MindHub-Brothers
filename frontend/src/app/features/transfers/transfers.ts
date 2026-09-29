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
  FormControl,
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
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, catchError, filter, of, startWith, switchMap } from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { ContactService } from '../../core/api/contact.service';
import { Contact } from '../../core/models/contact.model';
import { ContactDialog, ContactDialogData } from '../contacts/contact-dialog/contact-dialog';
import { IdempotentOperation, isOutcomeUnknown } from '../../core/api/idempotency';
import { TransferService } from '../../core/api/transfer.service';
import { Account } from '../../core/models/account.model';
import { secondFactorProblem } from '../../core/models/auth.model';
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
    MatAutocompleteModule,
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

  private readonly contactService = inject(ContactService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  /** Optional `?from=<accountId>` to preselect the source account. */
  readonly from = input<string>();
  /** Optional `?to=<accountNumber>` (e.g. from the recipients page) to prefill the destination. */
  readonly to = input<string>();

  protected readonly step = signal<Step>('form');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly receipt = signal<TransferReceipt | null>(null);
  /** Error shown on the confirmation step when the outcome is unknown (retry is safe). */
  protected readonly confirmError = signal<string | null>(null);
  /** The last attempt's outcome is unknown: the button offers to retry it (safely, same key). */
  protected readonly retryable = signal(false);
  /** Retrying the same transfer (even after going back and re-reviewing it) reuses its key. */
  private readonly operation = new IdempotentOperation();

  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly accountsState = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.accountService.getMyAccounts()))),
    { requireSync: true },
  );
  protected readonly accounts = computed(() => {
    const s = this.accountsState();
    return s.status === 'loaded' ? s.data : [];
  });

  /** Today's allowance for transfers to others (null until loaded or if it failed: the API still enforces it). */
  protected readonly limits = toSignal(
    this.reload$.pipe(
      switchMap(() => this.transferService.getLimits().pipe(catchError(() => of(null)))),
    ),
    { initialValue: null },
  );

  /** Saved recipients for the destination autocomplete (empty if they cannot be loaded). */
  private readonly contactsReload$ = new BehaviorSubject<void>(undefined);
  protected readonly contacts = toSignal(
    this.contactsReload$.pipe(
      switchMap(() => this.contactService.getMine().pipe(catchError(() => of<Contact[]>([])))),
    ),
    { initialValue: [] as Contact[] },
  );

  /** Authenticator code, asked for on the confirmation step when the transfer needs it. */
  protected readonly code = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
  });
  /** Set when the API asked for a code we did not anticipate. */
  private readonly codeRequested = signal(false);

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

  /** Daily limit and 2FA apply only to other people's accounts. */
  protected readonly toThirdParty = computed(
    () =>
      this.formValue().destination === 'third' &&
      !this.accounts().some((a) => a.number === this.targetNumber()),
  );

  /** The saved recipient for the typed destination, if any. */
  protected readonly matchedContact = computed(() =>
    this.formValue().destination === 'third'
      ? this.contacts().find((c) => c.accountNumber === this.targetNumber())
      : undefined,
  );

  /** Recipients matching what is typed in the destination (by alias, name or number). */
  protected readonly contactOptions = computed(() => {
    const typed = (this.formValue().thirdTarget ?? '').trim().toLowerCase();
    const all = this.contacts();
    if (!typed || this.matchedContact()) return this.matchedContact() ? [] : all;
    return all.filter((c) =>
      `${c.alias} ${c.holderDisplay} ${c.accountNumber}`.toLowerCase().includes(typed),
    );
  });

  protected readonly needsCode = computed(() => {
    const limits = this.limits();
    const amount = this.formValue().amount ?? 0;
    const anticipated =
      !!limits?.secondFactorEnabled &&
      this.toThirdParty() &&
      amount >= limits.secondFactorThreshold;
    return anticipated || this.codeRequested();
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
    // Prefill the destination from ?to= once (it stays editable).
    effect(() => {
      const to = this.to()?.trim().toUpperCase();
      if (to && !this.form.controls.thirdTarget.value) {
        this.form.controls.thirdTarget.setValue(to);
      }
    });
    // The amount is bounded by the balance and, to others, today's remaining limit. The validator
    // reads the signals when it runs; the effect only re-validates when they change.
    this.form.controls.amount.addValidators((control) => {
      const value = (control.value as number | null) ?? 0;
      if (value > (this.sourceAccount()?.balance ?? 0)) return { funds: true };
      const remaining = this.toThirdParty() ? this.limits()?.remainingToday : undefined;
      return remaining !== undefined && value > remaining ? { limit: true } : null;
    });
    effect(() => {
      this.sourceAccount();
      this.toThirdParty();
      this.limits();
      this.form.controls.amount.updateValueAndValidity({ emitEvent: false });
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
    this.form.controls.amount.updateValueAndValidity(); // destination may have just changed
    if (this.targetNumber() && this.targetNumber() === this.form.controls.source.value) {
      this.form.controls.thirdTarget.setErrors({ same: true });
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.code.reset();
    this.codeRequested.set(false);
    this.step.set('confirm');
  }

  protected back(): void {
    this.confirmError.set(null);
    this.retryable.set(false);
    this.step.set('form');
  }

  protected confirm(): void {
    if (this.needsCode() && this.code.invalid) {
      this.code.markAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: TransferRequest = {
      sourceAccountNumber: v.source,
      targetAccountNumber: this.targetNumber(),
      amount: v.amount ?? 0,
      description: v.description.trim() || undefined,
      secondFactorCode: this.needsCode() ? this.code.value : undefined,
    };
    this.submitting.set(true);
    this.confirmError.set(null);
    this.retryable.set(false);
    this.transferService.transfer(request, this.operation.keyFor(request)).subscribe({
      next: (receipt) => {
        this.operation.settle();
        this.submitting.set(false);
        this.receipt.set(receipt);
        this.step.set('done');
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.operation.settleUnlessUnknown(err);
        if (isOutcomeUnknown(err)) {
          // Network error or server failure: the transfer may or may not have happened. Stay here so
          // "Reintentar" resends the same Idempotency-Key and can never transfer twice.
          this.retryable.set(true);
          this.confirmError.set(
            'No pudimos confirmar si la transferencia se realizó. Reintentá: no se va a duplicar.',
          );
          return;
        }
        const problem = secondFactorProblem(err);
        if (problem) {
          // Stay here and ask for (another) code.
          this.codeRequested.set(true);
          this.code.reset();
          this.confirmError.set(
            problem === 'INVALID'
              ? 'El código no es correcto o ya venció. Probá con el que muestra tu app ahora.'
              : 'Para esta transferencia necesitamos el código de tu app de autenticación.',
          );
          return;
        }
        if (isLimitExceeded(err)) {
          this.reload$.next(); // refresh the allowance shown in the form
        }
        this.error.set(messageFor(err));
        this.step.set('form');
      },
    });
  }

  /** After a transfer to someone new: save them to the agenda. */
  protected saveContact(accountNumber: string): void {
    this.dialog
      .open<ContactDialog, ContactDialogData, Contact>(ContactDialog, {
        data: { accountNumber },
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((saved): saved is Contact => !!saved))
      .subscribe((saved) => {
        this.snackBar.open(`Guardaste a ${saved.alias} en tu agenda.`, 'OK');
        this.contactsReload$.next();
      });
  }

  /** Back to an empty form with fresh balances (the source is preselected again by the effect). */
  protected newTransfer(): void {
    this.receipt.set(null);
    this.error.set(null);
    this.confirmError.set(null);
    this.retryable.set(false);
    this.form.reset({ destination: 'third' });
    this.setDestination('third');
    this.step.set('form');
    this.reload$.next();
  }
}

function isLimitExceeded(err: unknown): boolean {
  return err instanceof HttpErrorResponse && err.error?.code === 'DAILY_LIMIT_EXCEEDED';
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (isLimitExceeded(err)) {
      const remaining = Number(err.error?.remaining ?? 0);
      return remaining > 0
        ? `Supera tu límite diario para transferir a terceros. Hoy podés enviar hasta ${formatArs(remaining)}.`
        : 'Ya usaste tu límite diario para transferir a terceros. Mañana se renueva.';
    }
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

function formatArs(value: number): string {
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(value);
}
