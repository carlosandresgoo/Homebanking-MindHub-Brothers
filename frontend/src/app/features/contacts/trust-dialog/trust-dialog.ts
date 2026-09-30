import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';

import { ContactService } from '../../../core/api/contact.service';
import { secondFactorProblem } from '../../../core/models/auth.model';
import { Contact } from '../../../core/models/contact.model';

export interface TrustDialogData {
  contact: Contact;
}

/** Marks a recipient as trusted after checking a code from the authenticator app. */
@Component({
  selector: 'app-trust-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    RouterLink,
  ],
  template: `
    <h2 mat-dialog-title class="title">
      <mat-icon>verified_user</mat-icon>
      Destinatario de confianza
    </h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        @if (error(); as message) {
          <div class="error-banner" role="alert">
            <mat-icon>error</mat-icon>
            <span>{{ message }}</span>
          </div>
        }
        @if (needsTwoFactor()) {
          <p>
            Para marcar destinatarios de confianza necesitás la verificación en dos pasos.
            <a routerLink="/profile" mat-dialog-close>Activala desde tu perfil.</a>
          </p>
        } @else {
          <p class="who">
            <strong>{{ data.contact.alias }}</strong>
            <span>{{ data.contact.holderDisplay }} · {{ data.contact.accountNumber }}</span>
          </p>
          <p class="help">
            Las transferencias grandes a esta cuenta no te van a pedir el código de tu app. El
            límite diario sigue igual. Marcá solo cuentas que conozcas bien.
          </p>
          <mat-form-field>
            <mat-label>Código de tu app de autenticación</mat-label>
            <mat-icon matPrefix>phonelink_lock</mat-icon>
            <input
              matInput
              id="trustCode"
              formControlName="code"
              inputmode="numeric"
              autocomplete="one-time-code"
              maxlength="6"
            />
            <mat-error>Ingresá los 6 dígitos del código.</mat-error>
          </mat-form-field>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close [disabled]="saving()">Cancelar</button>
        @if (!needsTwoFactor()) {
          <button mat-flat-button type="submit" class="submit" [disabled]="saving()">
            @if (saving()) {
              <mat-spinner diameter="20" aria-label="Verificando" />
            } @else {
              Confirmar
            }
          </button>
        }
      </mat-dialog-actions>
    </form>
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
      gap: 12px;
      min-width: min(420px, 80vw);
      padding-top: 8px !important;
    }
    .who {
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .who strong {
      color: var(--mat-sys-on-surface);
    }
    .help,
    p {
      margin: 0;
      font: var(--mat-sys-body-medium);
    }
    mat-icon[matPrefix] {
      margin: 0 4px 0 12px;
      color: var(--mat-sys-on-surface-variant);
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
    .submit {
      min-width: 120px;
    }
    mat-spinner {
      --mat-progress-spinner-active-indicator-color: currentColor;
      margin: 0 auto;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TrustDialog {
  private readonly contacts = inject(ContactService);
  private readonly dialogRef = inject<MatDialogRef<TrustDialog, Contact>>(MatDialogRef);
  protected readonly data = inject<TrustDialogData>(MAT_DIALOG_DATA);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** The API said 2FA is off: explain instead of asking for a code. */
  protected readonly needsTwoFactor = signal(false);

  protected readonly form = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{6}$/)],
    }),
  });
  private readonly code = this.form.controls.code;

  protected save(): void {
    if (this.code.invalid) {
      this.code.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.contacts.trust(this.data.contact.id, this.code.value).subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (err: unknown) => {
        this.saving.set(false);
        this.dialogRef.disableClose = false;
        if (err instanceof HttpErrorResponse && err.error?.code === 'TWO_FACTOR_REQUIRED') {
          this.needsTwoFactor.set(true);
          return;
        }
        this.code.reset();
        this.error.set(
          secondFactorProblem(err)
            ? 'El código no es correcto o ya venció. Probá con el que muestra tu app ahora.'
            : 'No pudimos marcarlo como de confianza. Intentá de nuevo.',
        );
      },
    });
  }
}
