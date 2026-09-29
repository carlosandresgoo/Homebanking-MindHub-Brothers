import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Brand } from '../../shared/brand/brand';

@Component({
  selector: 'app-forgot-password',
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
  template: `
    <div class="layout">
      <aside class="brand-panel">
        <app-brand [inverse]="true" />
        <div class="brand-copy">
          <h2>¿Olvidaste tu contraseña?</h2>
          <p>Te enviamos un enlace para crear una nueva. Por tu seguridad, vence en 30 minutos.</p>
        </div>
        <small>© MindHub Brothers</small>
      </aside>

      <main class="form-panel">
        <div class="form-card">
          <div class="mobile-brand"><app-brand /></div>
          @if (sent()) {
            <div class="done" role="status">
              <mat-icon class="done-icon">mark_email_read</mat-icon>
              <h1>Revisá tu correo</h1>
              <p class="subtitle">
                Si <strong>{{ form.controls.email.value }}</strong> corresponde a un cliente, te
                enviamos un enlace para restablecer la contraseña.
              </p>
              <a mat-flat-button class="submit" routerLink="/login">Volver a ingresar</a>
            </div>
          } @else {
            <h1>Restablecer contraseña</h1>
            <p class="subtitle">Ingresá el email con el que te registraste.</p>

            @if (error(); as message) {
              <div class="error-banner" role="alert">
                <mat-icon>error</mat-icon>
                <span>{{ message }}</span>
              </div>
            }

            <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
              <mat-form-field>
                <mat-label>Email</mat-label>
                <mat-icon matPrefix>mail</mat-icon>
                <input
                  matInput
                  id="email"
                  type="email"
                  formControlName="email"
                  autocomplete="email"
                />
                @if (form.controls.email.hasError('required')) {
                  <mat-error>Ingresá tu email.</mat-error>
                } @else if (form.controls.email.hasError('email')) {
                  <mat-error>El email no es válido.</mat-error>
                }
              </mat-form-field>
              <button mat-flat-button class="submit" type="submit" [disabled]="submitting()">
                @if (submitting()) {
                  <mat-spinner diameter="20" aria-label="Enviando" />
                } @else {
                  Enviar enlace
                }
              </button>
            </form>

            <a mat-button class="back" routerLink="/login">
              <mat-icon>arrow_back</mat-icon>
              Volver a ingresar
            </a>
          }
        </div>
      </main>
    </div>
  `,
  styleUrls: ['../login/login.scss', './password-pages.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);

  protected readonly submitting = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => {
        this.submitting.set(false);
        this.sent.set(true);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 429
            ? 'Demasiados intentos. Esperá un minuto y volvé a probar.'
            : 'No pudimos procesar el pedido. Intentá de nuevo más tarde.',
        );
      },
    });
  }
}
