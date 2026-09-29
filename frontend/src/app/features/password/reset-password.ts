import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import {
  PASSWORD_MIN,
  newPasswordValidators,
  passwordsMatch,
} from '../../core/validation/password';
import { Brand } from '../../shared/brand/brand';

@Component({
  selector: 'app-reset-password',
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
          <h2>Elegí una contraseña nueva.</h2>
          <p>Al cambiarla, cerramos tu sesión en todos los dispositivos.</p>
        </div>
        <small>© MindHub Brothers</small>
      </aside>

      <main class="form-panel">
        <div class="form-card">
          <div class="mobile-brand"><app-brand /></div>
          @if (!token()) {
            <div class="error-banner" role="alert">
              <mat-icon>link_off</mat-icon>
              <span>El enlace está incompleto. Pedí uno nuevo.</span>
            </div>
            <a mat-flat-button class="submit" routerLink="/forgot-password"
              >Pedir un enlace nuevo</a
            >
          } @else if (done()) {
            <div class="done" role="status">
              <mat-icon class="done-icon">check_circle</mat-icon>
              <h1>¡Listo!</h1>
              <p class="subtitle">Tu contraseña se cambió. Ya podés ingresar con la nueva.</p>
              <a mat-flat-button class="submit" routerLink="/login">Ingresar</a>
            </div>
          } @else {
            <h1>Nueva contraseña</h1>
            <p class="subtitle">Mínimo {{ passwordMin }} caracteres.</p>

            @if (error(); as message) {
              <div class="error-banner" role="alert">
                <mat-icon>error</mat-icon>
                <span>{{ message }}</span>
                @if (expired()) {
                  <a routerLink="/forgot-password">Pedir otro</a>
                }
              </div>
            }

            <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
              <mat-form-field>
                <mat-label>Nueva contraseña</mat-label>
                <mat-icon matPrefix>key</mat-icon>
                <input
                  matInput
                  id="newPassword"
                  type="password"
                  formControlName="newPassword"
                  autocomplete="new-password"
                />
                @if (form.controls.newPassword.hasError('required')) {
                  <mat-error>Elegí una contraseña.</mat-error>
                } @else if (form.controls.newPassword.invalid) {
                  <mat-error>Entre {{ passwordMin }} y 72 caracteres.</mat-error>
                }
              </mat-form-field>
              <mat-form-field>
                <mat-label>Repetí la contraseña</mat-label>
                <mat-icon matPrefix>key</mat-icon>
                <input
                  matInput
                  id="confirm"
                  type="password"
                  formControlName="confirm"
                  autocomplete="new-password"
                />
              </mat-form-field>
              @if (form.hasError('mismatch') && form.controls.confirm.touched) {
                <p class="field-error" role="alert">Las contraseñas no coinciden.</p>
              }
              <button mat-flat-button class="submit" type="submit" [disabled]="submitting()">
                @if (submitting()) {
                  <mat-spinner diameter="20" aria-label="Guardando" />
                } @else {
                  Cambiar contraseña
                }
              </button>
            </form>
          }
        </div>
      </main>
    </div>
  `,
  styleUrls: ['../login/login.scss', './password-pages.scss'],
  styles: `
    .field-error {
      margin: -8px 0 0 16px;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPassword {
  private readonly auth = inject(AuthService);

  /** `?token=` from the e-mail link (withComponentInputBinding). */
  readonly token = input<string>();

  protected readonly passwordMin = PASSWORD_MIN;
  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly expired = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group(
    { newPassword: ['', newPasswordValidators], confirm: [''] },
    { validators: passwordsMatch },
  );

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.resetPassword(this.token() ?? '', this.form.getRawValue().newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.done.set(true);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        const invalidLink = err instanceof HttpErrorResponse && err.status === 400;
        this.expired.set(invalidLink);
        this.error.set(
          invalidLink
            ? 'El enlace venció o ya fue usado.'
            : 'No pudimos cambiar la contraseña. Intentá de nuevo más tarde.',
        );
      },
    });
  }
}
