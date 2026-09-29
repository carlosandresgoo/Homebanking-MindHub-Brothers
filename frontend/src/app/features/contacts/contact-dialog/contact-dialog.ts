import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Observable } from 'rxjs';

import { ContactService } from '../../../core/api/contact.service';
import {
  CONTACT_ALIAS_MAX,
  CONTACT_ALIAS_PATTERN,
  Contact,
} from '../../../core/models/contact.model';

export interface ContactDialogData {
  /** Edit this recipient's alias; without it, the dialog adds a new one. */
  contact?: Contact;
  /** Prefills the account number when adding (e.g. right after a transfer). */
  accountNumber?: string;
}

/** Adds a recipient or renames one. Closes with the saved recipient, or nothing if cancelled. */
@Component({
  selector: 'app-contact-dialog',
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
      <mat-icon>{{ editing ? 'edit' : 'person_add' }}</mat-icon>
      {{ editing ? 'Editar alias' : 'Agregar destinatario' }}
    </h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        @if (error(); as message) {
          <div class="error-banner" role="alert">
            <mat-icon>error</mat-icon>
            <span>{{ message }}</span>
          </div>
        }
        @if (editing) {
          <p class="who">
            <strong>{{ data.contact!.holderDisplay }}</strong>
            <span class="hb-tabular">{{ data.contact!.accountNumber }}</span>
          </p>
        } @else {
          <mat-form-field>
            <mat-label>Número de cuenta</mat-label>
            <mat-icon matPrefix>tag</mat-icon>
            <input
              matInput
              id="contactAccount"
              formControlName="accountNumber"
              placeholder="VIN-12345678"
              autocomplete="off"
              class="uppercase"
            />
            <mat-error>Ingresá el número de cuenta.</mat-error>
          </mat-form-field>
        }
        <mat-form-field>
          <mat-label>Alias</mat-label>
          <mat-icon matPrefix>label</mat-icon>
          <input
            matInput
            id="contactAlias"
            formControlName="alias"
            [maxlength]="aliasMax"
            placeholder="Ej.: Mamá, Alquiler"
            autocomplete="off"
          />
          <mat-hint align="end">{{ form.controls.alias.value.length }}/{{ aliasMax }}</mat-hint>
          @if (form.controls.alias.hasError('required')) {
            <mat-error>Elegí un alias.</mat-error>
          } @else {
            <mat-error>Usá letras, números, espacios y . - _ ( )</mat-error>
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
    .uppercase {
      text-transform: uppercase;
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
export class ContactDialog {
  private readonly contacts = inject(ContactService);
  private readonly dialogRef = inject<MatDialogRef<ContactDialog, Contact>>(MatDialogRef);
  protected readonly data = inject<ContactDialogData>(MAT_DIALOG_DATA);

  protected readonly editing = !!this.data.contact;
  protected readonly aliasMax = CONTACT_ALIAS_MAX;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    accountNumber: [
      { value: this.data.accountNumber ?? '', disabled: this.editing },
      [Validators.required, Validators.maxLength(20)],
    ],
    alias: [
      this.data.contact?.alias ?? '',
      [
        Validators.required,
        Validators.maxLength(CONTACT_ALIAS_MAX),
        Validators.pattern(CONTACT_ALIAS_PATTERN),
      ],
    ],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { accountNumber, alias } = this.form.getRawValue();
    const request: Observable<Contact> = this.editing
      ? this.contacts.rename(this.data.contact!.id, alias.trim())
      : this.contacts.add(accountNumber.trim().toUpperCase(), alias.trim());
    this.saving.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    request.subscribe({
      next: (saved) => this.dialogRef.close(saved),
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
    if (err.status === 404) return 'No encontramos esa cuenta. Revisá el número.';
    if (err.status === 409) {
      return err.error?.detail === 'This account is already in your recipients'
        ? 'Esa cuenta ya está en tu agenda.'
        : 'Ya tenés un destinatario con ese alias.';
    }
    if (err.status === 422) {
      return err.error?.detail?.startsWith('Your own accounts')
        ? 'Es una cuenta tuya: ya aparece siempre al transferir.'
        : 'Tu agenda está llena. Borrá algún destinatario para agregar otro.';
    }
    if (err.status === 429) return 'Demasiados intentos. Esperá un minuto y volvé a probar.';
  }
  return 'No pudimos guardar el destinatario. Intentá de nuevo.';
}
