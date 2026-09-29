import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ErrorStateMatcher } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Role, secondFactorProblem } from '../../core/models/auth.model';
import { Brand } from '../../shared/brand/brand';

@Component({
  selector: 'app-login',
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
  templateUrl: './login.html',
  styleUrl: './login.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Bound from the `?returnUrl=` query param (withComponentInputBinding). */
  readonly returnUrl = input<string>();

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  /** Second step for clients with 2FA: the password was right, now the app code. */
  protected readonly needsCode = signal(false);
  /** The form was already submitted once: the new code field should not start out red. */
  protected readonly touchedOnly: ErrorStateMatcher = {
    isErrorState: (control) => !!control && control.invalid && control.touched,
  };

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    code: [{ value: '', disabled: true }, [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password, code } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.auth
      .login({ email, password, secondFactorCode: this.needsCode() ? code : undefined })
      .subscribe({
        next: (role) => void this.router.navigateByUrl(this.targetUrl(role)),
        error: (err: unknown) => {
          this.submitting.set(false);
          const problem = secondFactorProblem(err);
          if (problem) {
            this.askForCode();
            this.error.set(
              problem === 'INVALID'
                ? 'El código no es correcto o ya venció. Probá con el que muestra tu app ahora.'
                : null,
            );
            return;
          }
          this.backToPassword();
          this.error.set(messageFor(err));
        },
      });
  }

  /** Leaves the code step (e.g. to use another account). */
  protected backToPassword(): void {
    this.needsCode.set(false);
    this.form.controls.code.disable();
    this.form.controls.password.reset();
  }

  private askForCode(): void {
    this.needsCode.set(true);
    this.form.controls.code.enable();
    this.form.controls.code.reset();
  }

  private targetUrl(role: Role): string {
    const returnUrl = this.returnUrl();
    // Only same-app paths: never redirect to an absolute/external URL taken from the query string.
    if (returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//')) {
      return returnUrl;
    }
    return role === 'ADMIN' ? '/manager' : '/accounts';
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 401) return 'El email o la contraseña no son correctos.';
    if (err.status === 429) return 'Demasiados intentos. Esperá un minuto y volvé a probar.';
    if (err.status === 423) return lockedMessage(err.error);
  }
  return 'No pudimos iniciar sesión. Intentá de nuevo más tarde.';
}

function lockedMessage(problem: { reason?: string; lockedUntil?: string } | null): string {
  if (problem?.reason === 'BLOCKED') {
    return 'Tu usuario está bloqueado. Comunicate con el banco para habilitarlo.';
  }
  const until = problem?.lockedUntil ? new Date(problem.lockedUntil) : null;
  const time = until
    ? until.toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' })
    : null;
  return time
    ? `Por seguridad bloqueamos tu usuario tras varios intentos fallidos. Probá de nuevo a las ${time}.`
    : 'Por seguridad bloqueamos tu usuario tras varios intentos fallidos. Probá de nuevo más tarde.';
}
