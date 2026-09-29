import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { PASSWORD_MIN, newPasswordValidators } from '../../core/validation/password';
import { PERSON_NAME_ERROR, personNameValidators } from '../../core/validation/person-name';
import { Brand } from '../../shared/brand/brand';

@Component({
  selector: 'app-register',
  imports: [
    Brand,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  templateUrl: './register.html',
  styleUrl: '../login/login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly passwordMin = PASSWORD_MIN;
  protected readonly nameError = PERSON_NAME_ERROR;
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', personNameValidators],
    lastName: ['', personNameValidators],
    email: ['', [Validators.required, Validators.email]],
    password: ['', newPasswordValidators],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/accounts'),
      error: (err: unknown) => {
        this.submitting.set(false);
        this.form.controls.password.reset();
        this.error.set(messageFor(err));
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.form.controls.email.setErrors({ taken: true });
        }
      },
    });
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 409) return 'Ya existe una cuenta con ese email. ¿Querés ingresar?';
    if (err.status === 400) return 'Revisá los datos del formulario.';
    if (err.status === 429) return 'Demasiados intentos. Esperá un minuto y volvé a probar.';
  }
  return 'No pudimos crear tu cuenta. Intentá de nuevo más tarde.';
}
