import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { startWith } from 'rxjs';

import { AccountService } from '../../../core/api/account.service';
import { IdempotentOperation, isOutcomeUnknown } from '../../../core/api/idempotency';
import { ScheduledTransferService } from '../../../core/api/scheduled-transfer.service';
import { Account, Recipient } from '../../../core/models/account.model';
import { secondFactorProblem } from '../../../core/models/auth.model';
import {
  CreateScheduledTransferRequest,
  FREQUENCY_LABEL,
  Frequency,
  ScheduledTransfer,
} from '../../../core/models/scheduled-transfer.model';
import { bankDate } from '../../../core/utils/bank-date';

export interface ScheduleDialogData {
  /** The client's open accounts (sources, and own destinations). */
  accounts: Account[];
}

/** Positive amount with at most two decimals. */
function amountFormat(control: AbstractControl<number | null>): ValidationErrors | null {
  const value = control.value;
  if (value === null || value === undefined || Number.isNaN(value)) return null;
  if (value < 0.01) return { min: true };
  return Math.round(value * 100) / 100 === value ? null : { decimals: true };
}

/** Schedules a transfer: data → confirmation with the recipient (and a 2FA code if needed). */
@Component({
  selector: 'app-schedule-dialog',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title class="title">
      <mat-icon>event_repeat</mat-icon>
      Programar transferencia
    </h2>
    @if (step() === 'form') {
      <form [formGroup]="form" (ngSubmit)="review()" novalidate>
        <mat-dialog-content class="content">
          @if (error(); as message) {
            <div class="error-banner" role="alert">
              <mat-icon>error</mat-icon>
              <span>{{ message }}</span>
            </div>
          }
          <mat-form-field>
            <mat-label>Desde</mat-label>
            <mat-select formControlName="source" id="scheduleSource">
              @for (account of data.accounts; track account.id) {
                <mat-option [value]="account.number">
                  {{ account.number }} · {{ account.balance | currency }}
                </mat-option>
              }
            </mat-select>
            <mat-error>Elegí la cuenta de origen.</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Hacia (CBU, alias o número de cuenta)</mat-label>
            <input
              matInput
              id="scheduleTarget"
              formControlName="target"
              maxlength="24"
              autocomplete="off"
            />
            <mat-error>Ingresá el CBU, alias o número de cuenta.</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Importe</mat-label>
            <span matTextPrefix>$&nbsp;</span>
            <input
              matInput
              id="scheduleAmount"
              type="number"
              inputmode="decimal"
              step="0.01"
              formControlName="amount"
              class="hb-tabular"
            />
            <mat-error>Ingresá un importe mayor a cero, con hasta 2 decimales.</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label>Descripción (opcional)</mat-label>
            <input
              matInput
              id="scheduleDescription"
              formControlName="description"
              maxlength="100"
            />
          </mat-form-field>

          <div class="frequency">
            <span class="label" id="frequency-label">¿Cada cuánto?</span>
            <mat-button-toggle-group formControlName="frequency" aria-labelledby="frequency-label">
              @for (f of frequencies; track f) {
                <mat-button-toggle [value]="f">{{ frequencyLabel[f] }}</mat-button-toggle>
              }
            </mat-button-toggle-group>
          </div>

          <div class="row">
            <mat-form-field>
              <mat-label>{{ recurring() ? 'Primera transferencia' : 'Fecha' }}</mat-label>
              <input
                matInput
                id="scheduleStart"
                type="date"
                formControlName="startDate"
                [min]="minDate"
              />
              <mat-error>Elegí una fecha desde mañana.</mat-error>
            </mat-form-field>
            @if (recurring()) {
              <mat-form-field>
                <mat-label>Cantidad de veces (opcional)</mat-label>
                <input
                  matInput
                  id="scheduleMaxRuns"
                  type="number"
                  min="1"
                  max="120"
                  step="1"
                  formControlName="maxRuns"
                />
                <mat-hint>Vacío: hasta que la canceles.</mat-hint>
                <mat-error>Entre 1 y 120.</mat-error>
              </mat-form-field>
            }
          </div>
        </mat-dialog-content>
        <mat-dialog-actions align="end">
          <button mat-button type="button" mat-dialog-close>Cancelar</button>
          <button mat-flat-button type="submit" class="submit" [disabled]="busy()">
            @if (busy()) {
              <mat-spinner diameter="20" aria-label="Verificando la cuenta destino" />
            } @else {
              Continuar
            }
          </button>
        </mat-dialog-actions>
      </form>
    } @else {
      <mat-dialog-content class="content" aria-label="Confirmación">
        @let v = form.getRawValue();
        <p class="lead">Revisá los datos antes de programarla.</p>
        <dl class="summary">
          <div>
            <dt>Importe</dt>
            <dd class="hb-tabular">{{ v.amount | currency }}</dd>
          </div>
          <div>
            <dt>Desde</dt>
            <dd class="hb-tabular">{{ v.source }}</dd>
          </div>
          <div>
            <dt>Hacia</dt>
            <dd>
              @if (recipient(); as r) {
                {{ r.own ? 'Cuenta propia' : r.holderDisplay }} ·
                <span class="hb-tabular">{{ r.accountNumber }}</span>
              } @else {
                Cuenta propia · <span class="hb-tabular">{{ targetNumber() }}</span>
              }
            </dd>
          </div>
          <div>
            <dt>Cuándo</dt>
            <dd>
              {{ frequencyLabel[v.frequency] }}
              {{ recurring() ? 'desde el' : 'el' }} {{ v.startDate | date: 'longDate' }}
              @if (recurring() && v.maxRuns) {
                · {{ v.maxRuns }} {{ v.maxRuns === 1 ? 'vez' : 'veces' }}
              }
            </dd>
          </div>
        </dl>
        <p class="muted">
          La hacemos a primera hora de cada fecha. Si ese día no alcanza el saldo o el límite
          diario, te avisamos y queda para la próxima.
        </p>
        @if (needsCode()) {
          <mat-form-field>
            <mat-label>Código de tu app de autenticación</mat-label>
            <input
              matInput
              id="scheduleCode"
              [formControl]="code"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
            />
            <mat-hint>Autoriza todas las transferencias de esta programación.</mat-hint>
            <mat-error>Ingresá los 6 dígitos del código.</mat-error>
          </mat-form-field>
        }
        @if (error(); as message) {
          <div class="error-banner" role="alert">
            <mat-icon>error</mat-icon>
            <span>{{ message }}</span>
          </div>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="back()" [disabled]="busy()">Volver</button>
        <button
          mat-flat-button
          type="button"
          class="confirm"
          (click)="confirm()"
          [disabled]="busy()"
        >
          @if (busy()) {
            <mat-spinner diameter="20" aria-label="Programando" />
          } @else {
            Programar
          }
        </button>
      </mat-dialog-actions>
    }
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
    .content {
      display: grid;
      gap: 8px;
      min-width: min(460px, 80vw);
      padding-top: 8px !important;
    }
    .frequency {
      display: grid;
      gap: 6px;
      margin-bottom: 12px;
    }
    .label,
    .muted {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
    .row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
    }
    .lead {
      margin: 0;
      font: var(--mat-sys-body-large);
    }
    .summary {
      margin: 0;
    }
    .summary div {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      padding: 10px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .summary dt {
      color: var(--mat-sys-on-surface-variant);
    }
    .summary dd {
      margin: 0;
      text-align: right;
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
    .submit,
    .confirm {
      min-width: 130px;
    }
    mat-spinner {
      --mat-progress-spinner-active-indicator-color: currentColor;
      margin: 0 auto;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduleDialog {
  private readonly accountService = inject(AccountService);
  private readonly scheduledService = inject(ScheduledTransferService);
  private readonly dialogRef =
    inject<MatDialogRef<ScheduleDialog, ScheduledTransfer>>(MatDialogRef);
  protected readonly data = inject<ScheduleDialogData>(MAT_DIALOG_DATA);

  protected readonly frequencies: Frequency[] = ['ONCE', 'WEEKLY', 'MONTHLY'];
  protected readonly frequencyLabel = FREQUENCY_LABEL;
  /** The bank's tomorrow (the API rejects anything earlier). */
  protected readonly minDate = bankDate(1);

  protected readonly step = signal<'form' | 'confirm'>('form');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Who receives it (null for one of the client's own accounts, known without asking). */
  protected readonly recipient = signal<Recipient | null>(null);
  protected readonly needsCode = signal(false);
  private readonly operation = new IdempotentOperation();

  protected readonly form = inject(NonNullableFormBuilder).group({
    source: [
      this.data.accounts.find((a) => a.balance > 0)?.number ?? this.data.accounts[0]?.number ?? '',
      Validators.required,
    ],
    target: ['', [Validators.required, Validators.maxLength(24)]],
    amount: [null as number | null, [Validators.required, amountFormat]],
    description: ['', Validators.maxLength(100)],
    frequency: ['MONTHLY' as Frequency],
    startDate: [
      this.minDate,
      [
        Validators.required,
        (c: AbstractControl<string>) => (c.value && c.value < this.minDate ? { past: true } : null),
      ],
    ],
    maxRuns: [null as number | null, [Validators.min(1), Validators.max(120)]],
  });

  protected readonly code = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
  });

  private readonly frequency = toSignal(
    this.form.controls.frequency.valueChanges.pipe(startWith(this.form.controls.frequency.value)),
    { requireSync: true },
  );
  protected readonly recurring = computed(() => this.frequency() !== 'ONCE');

  protected targetNumber(): string {
    return this.form.controls.target.value.trim().toUpperCase();
  }

  protected review(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    const own = this.data.accounts.some((a) => a.number === this.targetNumber());
    if (own) {
      this.recipient.set(null);
      this.toConfirm();
      return;
    }
    this.busy.set(true);
    this.accountService.lookup(this.form.controls.target.value).subscribe({
      next: (recipient) => {
        this.busy.set(false);
        this.recipient.set(recipient);
        this.toConfirm();
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(messageFor(err));
      },
    });
  }

  protected back(): void {
    this.error.set(null);
    this.step.set('form');
  }

  protected confirm(): void {
    if (this.needsCode() && this.code.invalid) {
      this.code.markAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: CreateScheduledTransferRequest = {
      sourceAccountNumber: v.source,
      // The resolved number: what the client confirmed, even if the alias changes hands meanwhile.
      targetAccountNumber: this.recipient()?.accountNumber ?? this.targetNumber(),
      amount: v.amount ?? 0,
      description: v.description.trim() || undefined,
      frequency: v.frequency,
      startDate: v.startDate,
      maxRuns: v.frequency !== 'ONCE' && v.maxRuns ? v.maxRuns : undefined,
      secondFactorCode: this.needsCode() ? this.code.value : undefined,
    };
    this.busy.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.scheduledService.create(request, this.operation.keyFor(request)).subscribe({
      next: (created) => {
        this.operation.settle();
        this.dialogRef.close(created);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.dialogRef.disableClose = false;
        this.operation.settleUnlessUnknown(err);
        const problem = secondFactorProblem(err);
        if (problem) {
          this.needsCode.set(true);
          this.code.reset();
          this.error.set(
            problem === 'INVALID'
              ? 'El código no es correcto o ya venció. Probá con el que muestra tu app ahora.'
              : 'Por el importe, necesitamos el código de tu app de autenticación.',
          );
          return;
        }
        this.error.set(
          isOutcomeUnknown(err)
            ? 'No pudimos confirmar si se programó. Reintentá: no se va a duplicar.'
            : messageFor(err),
        );
      },
    });
  }

  private toConfirm(): void {
    this.needsCode.set(false);
    this.code.reset();
    this.step.set('confirm');
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const code = err.error?.code as string | undefined;
    if (err.status === 404) return 'No encontramos una cuenta con ese CBU, alias o número.';
    if (code === 'INVALID_CBU') return 'El CBU no es válido: revisá los 22 dígitos.';
    if (code === 'OTHER_BANK')
      return 'Por ahora solo podés transferir a cuentas de MindHub Brothers.';
    if (code === 'START_DATE') return 'La primera transferencia tiene que ser desde mañana.';
    if (code === 'TOO_MANY_SCHEDULED') {
      return 'Llegaste al máximo de transferencias programadas. Cancelá alguna para agregar otra.';
    }
    if (err.status === 422) return 'No podés programar una transferencia a la misma cuenta.';
    if (err.status === 429) return 'Hiciste muchas consultas seguidas. Esperá un minuto.';
    if (err.status === 400) return 'Revisá los datos de la transferencia.';
  }
  return 'No pudimos programar la transferencia. Intentá de nuevo.';
}
