import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import { TwoFactorService } from '../../../core/api/two-factor.service';
import { secondFactorProblem } from '../../../core/models/auth.model';
import { Client } from '../../../core/models/client.model';

/** 6 digits, as shown by authenticator apps. */
export const CODE_PATTERN = /^\d{6}$/;

type Mode = 'idle' | 'enrolling' | 'disabling';

/** Turns the authenticator-app second factor on (scan + confirm) or off (password + code). */
@Component({
  selector: 'app-two-factor-card',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  templateUrl: './two-factor-card.html',
  styleUrl: './two-factor-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TwoFactorCard {
  private readonly twoFactor = inject(TwoFactorService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly enabled = input.required<boolean>();
  /** Emits the updated client after enabling or disabling. */
  readonly changed = output<Client>();

  protected readonly mode = signal<Mode>('idle');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly qr = signal<string | null>(null);
  protected readonly secret = signal<string | null>(null);

  protected readonly enrollForm = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(CODE_PATTERN)]],
  });
  protected readonly disableForm = this.fb.group({
    password: ['', Validators.required],
    code: ['', [Validators.required, Validators.pattern(CODE_PATTERN)]],
  });

  protected startEnrollment(): void {
    this.busy.set(true);
    this.error.set(null);
    this.twoFactor.setup().subscribe({
      next: (setup) => {
        this.secret.set(groupSecret(setup.secret));
        this.qr.set(null);
        this.enrollForm.reset();
        this.busy.set(false);
        this.mode.set('enrolling');
        void this.renderQr(setup.otpauthUri);
      },
      error: () => {
        this.busy.set(false);
        this.error.set('No pudimos iniciar la activación. Intentá de nuevo.');
      },
    });
  }

  protected confirmEnrollment(): void {
    if (this.enrollForm.invalid) {
      this.enrollForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.twoFactor.enable(this.enrollForm.getRawValue().code).subscribe({
      next: (client) => {
        this.finish();
        this.snackBar.open('Activaste la verificación en dos pasos.', 'OK');
        this.changed.emit(client);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.enrollForm.reset();
        this.error.set(codeMessage(err));
      },
    });
  }

  protected startDisabling(): void {
    this.disableForm.reset();
    this.error.set(null);
    this.mode.set('disabling');
  }

  protected confirmDisabling(): void {
    if (this.disableForm.invalid) {
      this.disableForm.markAllAsTouched();
      return;
    }
    const { password, code } = this.disableForm.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.twoFactor.disable(password, code).subscribe({
      next: (client) => {
        this.finish();
        this.snackBar.open('Desactivaste la verificación en dos pasos.', 'OK');
        this.changed.emit(client);
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.disableForm.reset();
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 422
            ? 'La contraseña no es correcta.'
            : codeMessage(err),
        );
      },
    });
  }

  protected cancel(): void {
    this.finish();
  }

  /** Loaded on demand: only people enrolling need the QR library. Without it, the key can be typed. */
  private async renderQr(uri: string): Promise<void> {
    try {
      const { toDataURL } = await import('qrcode');
      const image = await toDataURL(uri, { width: 208, margin: 1 });
      if (this.mode() === 'enrolling') this.qr.set(image);
    } catch {
      // Keep the manual key only.
    }
  }

  private finish(): void {
    this.busy.set(false);
    this.error.set(null);
    this.qr.set(null);
    this.secret.set(null);
    this.mode.set('idle');
  }
}

/** "JBSWY3DPEHPK3PXP" → "JBSW Y3DP EHPK 3PXP", easier to type. */
function groupSecret(secret: string): string {
  return secret.replace(/(.{4})(?=.)/g, '$1 ');
}

function codeMessage(err: unknown): string {
  if (secondFactorProblem(err) === 'INVALID') {
    return 'El código no es correcto o ya venció. Probá con el que muestra tu app ahora.';
  }
  if (err instanceof HttpErrorResponse && err.status === 429) {
    return 'Demasiados intentos. Esperá un minuto y volvé a probar.';
  }
  return 'No pudimos completar la operación. Intentá de nuevo.';
}
