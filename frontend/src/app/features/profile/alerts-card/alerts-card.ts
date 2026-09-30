import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { startWith, tap } from 'rxjs';

import { NotificationService } from '../../../core/api/notification.service';
import { AlertSettings } from '../../../core/models/notification.model';
import { toLoadState } from '../../../core/utils/load-state';

/** Required while its toggle is on: at least 0.01 with up to 2 decimals. */
function thresholdWhen(toggle: FormControl<boolean>) {
  return (control: AbstractControl<number | null>): ValidationErrors | null => {
    if (!toggle.value) return null;
    const value = control.value;
    if (value === null || value === undefined || Number.isNaN(value)) return { required: true };
    if (value < 0.01) return { min: true };
    return Math.round(value * 100) / 100 === value ? null : { decimals: true };
  };
}

/** The client's alerts: low balance, large debits, sign-ins, and whether to e-mail them. */
@Component({
  selector: 'app-alerts-card',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    ReactiveFormsModule,
  ],
  template: `
    <section class="card" aria-labelledby="alerts-title">
      <h2 id="alerts-title"><mat-icon>notifications_active</mat-icon> Alertas</h2>
      <p class="muted">Te avisamos en la campanita y, si querés, también por e-mail.</p>

      @let s = state();
      @if (s.status === 'loading') {
        <p class="muted">Cargando…</p>
      } @else if (s.status === 'error') {
        <p class="muted" role="alert">No pudimos cargar tus alertas.</p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="option">
            <mat-slide-toggle formControlName="lowBalanceOn" id="lowBalanceOn">
              Saldo bajo
            </mat-slide-toggle>
            <span class="hint">Cuando un débito deja una cuenta por debajo de este monto.</span>
            @if (form.controls.lowBalanceOn.value) {
              <mat-form-field class="amount">
                <mat-label>Avisarme debajo de</mat-label>
                <span matTextPrefix>$&nbsp;</span>
                <input
                  matInput
                  id="lowBalance"
                  type="number"
                  inputmode="decimal"
                  step="0.01"
                  formControlName="lowBalance"
                  class="hb-tabular"
                />
                <mat-error>Ingresá un monto mayor a cero, con hasta 2 decimales.</mat-error>
              </mat-form-field>
            }
          </div>

          <div class="option">
            <mat-slide-toggle formControlName="largeMovementOn" id="largeMovementOn">
              Débitos grandes
            </mat-slide-toggle>
            <span class="hint"
              >Por cada débito igual o mayor a este monto, en cualquier cuenta.</span
            >
            @if (form.controls.largeMovementOn.value) {
              <mat-form-field class="amount">
                <mat-label>Avisarme desde</mat-label>
                <span matTextPrefix>$&nbsp;</span>
                <input
                  matInput
                  id="largeMovement"
                  type="number"
                  inputmode="decimal"
                  step="0.01"
                  formControlName="largeMovement"
                  class="hb-tabular"
                />
                <mat-error>Ingresá un monto mayor a cero, con hasta 2 decimales.</mat-error>
              </mat-form-field>
            }
          </div>

          <div class="option">
            <mat-slide-toggle formControlName="loginAlerts" id="loginAlerts">
              Ingresos a mi cuenta
            </mat-slide-toggle>
            <span class="hint"
              >Con el dispositivo y la IP de cada ingreso, para detectar accesos ajenos.</span
            >
          </div>

          <div class="option">
            <mat-slide-toggle formControlName="emailAlerts" id="emailAlerts">
              Recibirlas también por e-mail
            </mat-slide-toggle>
            <span class="hint">
              Incluye los avisos de transferencias y plazos fijos. Los cambios de contraseña y de
              verificación en dos pasos te llegan siempre.
            </span>
          </div>

          <div class="actions">
            <button
              mat-flat-button
              type="submit"
              class="save"
              [disabled]="saving() || form.pristine"
            >
              @if (saving()) {
                <mat-spinner diameter="20" aria-label="Guardando" />
              } @else {
                Guardar alertas
              }
            </button>
          </div>
        </form>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .card {
      display: grid;
      gap: 14px;
      margin-bottom: 20px;
      padding: 24px;
      border-radius: var(--hb-radius);
      background: var(--mat-sys-surface);
      border: 1px solid var(--mat-sys-outline-variant);
    }
    h2 {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
      font: var(--mat-sys-title-large);
    }
    h2 mat-icon {
      color: var(--mat-sys-primary);
    }
    .muted,
    .hint {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
    form {
      display: grid;
      gap: 18px;
    }
    .option {
      display: grid;
      gap: 6px;
      padding-bottom: 16px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .hint {
      font: var(--mat-sys-body-small);
      padding-left: 4px;
    }
    .amount {
      max-width: 280px;
      margin-top: 4px;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
    }
    .save {
      min-width: 160px;
    }
    mat-spinner {
      --mat-progress-spinner-active-indicator-color: currentColor;
      margin: 0 auto;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlertsCard {
  private readonly api = inject(NotificationService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly saving = signal(false);

  private readonly lowBalanceOn = this.fb.control(false);
  private readonly largeMovementOn = this.fb.control(false);

  protected readonly form = this.fb.group({
    lowBalanceOn: this.lowBalanceOn,
    lowBalance: this.fb.control<number | null>(null, thresholdWhen(this.lowBalanceOn)),
    largeMovementOn: this.largeMovementOn,
    largeMovement: this.fb.control<number | null>(null, thresholdWhen(this.largeMovementOn)),
    loginAlerts: [true],
    emailAlerts: [true],
  });

  protected readonly state = toSignal(
    toLoadState(this.api.getAlerts().pipe(tap((settings) => this.fill(settings)))),
    { requireSync: true },
  );

  constructor() {
    // A threshold is validated only while its toggle is on.
    for (const [toggle, amount] of [
      [this.form.controls.lowBalanceOn, this.form.controls.lowBalance],
      [this.form.controls.largeMovementOn, this.form.controls.largeMovement],
    ] as const) {
      toggle.valueChanges
        .pipe(startWith(toggle.value), takeUntilDestroyed())
        .subscribe(() => amount.updateValueAndValidity());
    }
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const settings: AlertSettings = {
      lowBalanceThreshold: v.lowBalanceOn ? v.lowBalance : null,
      largeMovementThreshold: v.largeMovementOn ? v.largeMovement : null,
      loginAlerts: v.loginAlerts,
      emailAlerts: v.emailAlerts,
    };
    this.saving.set(true);
    this.api.updateAlerts(settings).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.fill(saved);
        this.snackBar.open('Guardamos tus alertas.', 'OK', { duration: 3000 });
      },
      error: () => {
        this.saving.set(false);
        this.snackBar.open('No pudimos guardar tus alertas. Revisá los montos.', 'OK');
      },
    });
  }

  private fill(settings: AlertSettings): void {
    this.form.reset({
      lowBalanceOn: settings.lowBalanceThreshold !== null,
      lowBalance: settings.lowBalanceThreshold,
      largeMovementOn: settings.largeMovementThreshold !== null,
      largeMovement: settings.largeMovementThreshold,
      loginAlerts: settings.loginAlerts,
      emailAlerts: settings.emailAlerts,
    });
  }
}
