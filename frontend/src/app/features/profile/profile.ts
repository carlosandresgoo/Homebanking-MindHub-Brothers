import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormGroupDirective,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';

import { ClientService } from '../../core/api/client.service';
import { AuthService } from '../../core/auth/auth.service';
import { toLoadState } from '../../core/utils/load-state';
import {
  PASSWORD_MIN,
  newPasswordValidators,
  passwordsMatch,
} from '../../core/validation/password';
import { initials } from '../../shared/initials';
import { AlertsCard } from './alerts-card/alerts-card';
import { TwoFactorCard } from './two-factor-card/two-factor-card';

@Component({
  selector: 'app-profile',
  imports: [
    AlertsCard,
    TwoFactorCard,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Profile {
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly passwordMin = PASSWORD_MIN;
  protected readonly state = toSignal(toLoadState(inject(ClientService).getCurrentClient()), {
    requireSync: true,
  });
  /** Follows the loaded client, then whatever the 2FA card reports. */
  protected readonly twoFactorEnabled = linkedSignal(() => {
    const s = this.state();
    return s.status === 'loaded' && !!s.data.twoFactorEnabled;
  });
  protected readonly initials = computed(() => {
    const s = this.state();
    return s.status === 'loaded' ? initials(s.data.name, s.data.lastName) : '';
  });

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly passwordForm = inject(NonNullableFormBuilder).group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', newPasswordValidators],
      confirm: [''],
    },
    { validators: passwordsMatch },
  );

  protected changePassword(formDirective: FormGroupDirective): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.saving.set(false);
        formDirective.resetForm();
        this.snackBar.open('Contraseña actualizada. Cerramos tus otras sesiones.', 'OK');
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.passwordForm.controls.currentPassword.reset();
        this.error.set(messageFor(err));
      },
    });
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse && err.status === 422) {
    return err.error?.detail === 'The current password is incorrect'
      ? 'La contraseña actual no es correcta.'
      : 'La nueva contraseña tiene que ser distinta de la actual.';
  }
  return 'No pudimos cambiar la contraseña. Intentá de nuevo más tarde.';
}
