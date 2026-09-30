import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AccountService } from '../../../core/api/account.service';
import { ACCOUNT_ALIAS_PATTERN, Account } from '../../../core/models/account.model';

export interface AliasDialogData {
  account: Pick<Account, 'id' | 'number' | 'alias'>;
}

/** Changes the alias of one of the client's accounts. Closes with the updated account, or nothing. */
@Component({
  selector: 'app-alias-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  template: `
    <h2 mat-dialog-title class="title">
      <mat-icon>alternate_email</mat-icon>
      Cambiar alias
    </h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        @if (error(); as message) {
          <div class="error-banner" role="alert">
            <mat-icon>error</mat-icon>
            <span>{{ message }}</span>
          </div>
        }
        <p class="help">
          Es lo que compartís para que te transfieran a la cuenta
          <strong class="hb-tabular">{{ data.account.number }}</strong
          >. Elegí algo fácil de dictar y que no revele datos personales.
        </p>
        <mat-form-field>
          <mat-label>Nuevo alias</mat-label>
          <mat-icon matPrefix>alternate_email</mat-icon>
          <input
            matInput
            id="accountAlias"
            formControlName="alias"
            maxlength="20"
            placeholder="sol.rio.mate"
            autocomplete="off"
            class="lowercase"
          />
          <mat-hint align="end">{{ form.controls.alias.value.trim().length }}/20</mat-hint>
          @if (form.controls.alias.hasError('required')) {
            <mat-error>Elegí un alias.</mat-error>
          } @else {
            <mat-error
              >De 6 a 20 caracteres: letras sin tildes ni ñ, números, puntos o guiones.</mat-error
            >
          }
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close [disabled]="saving()">Cancelar</button>
        <button mat-flat-button type="submit" class="submit" [disabled]="saving()">
          @if (saving()) {
            <mat-spinner diameter="20" aria-label="Guardando" />
          } @else {
            Guardar
          }
        </button>
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
    .help {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
    .lowercase {
      text-transform: lowercase;
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
export class AliasDialog {
  private readonly accounts = inject(AccountService);
  private readonly dialogRef = inject<MatDialogRef<AliasDialog, Account>>(MatDialogRef);
  protected readonly data = inject<AliasDialogData>(MAT_DIALOG_DATA);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    alias: [
      this.data.account.alias,
      [
        Validators.required,
        // Surrounding spaces are dropped when saving: validate what will actually be saved.
        (c: AbstractControl<string>): ValidationErrors | null =>
          !c.value.trim() || ACCOUNT_ALIAS_PATTERN.test(c.value.trim()) ? null : { pattern: true },
      ],
    ],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const alias = this.form.getRawValue().alias.trim().toLowerCase();
    if (alias === this.data.account.alias) {
      this.dialogRef.close();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.accounts.changeAlias(this.data.account.id, alias).subscribe({
      next: (account) => this.dialogRef.close(account),
      error: (err: unknown) => {
        this.saving.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(messageFor(err));
      },
    });
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 409) return 'Ese alias ya lo usa otra cuenta. Probá con otro.';
    if (err.status === 422) return 'Ese alias está reservado porque parece un número de cuenta.';
    if (err.status === 400) return 'Revisá el formato del alias.';
    if (err.status === 429) return 'Hiciste muchos cambios seguidos. Esperá un minuto.';
  }
  return 'No pudimos cambiar el alias. Intentá de nuevo.';
}
