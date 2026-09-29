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
import { Brand } from '../../shared/brand/brand';

/** Same rules as the backend's CreateClientRequest (BCrypt ignores bytes past 72). */
const LETTERS_ONLY = /^[a-zA-Z]+$/;
const PASSWORD_MIN = 12;
const PASSWORD_MAX = 72;

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
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(LETTERS_ONLY)]],
    lastName: [
      '',
      [Validators.required, Validators.maxLength(50), Validators.pattern(LETTERS_ONLY)],
    ],
    email: ['', [Validators.required, Validators.email]],
    password: [
      '',
      [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)],
    ],
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
