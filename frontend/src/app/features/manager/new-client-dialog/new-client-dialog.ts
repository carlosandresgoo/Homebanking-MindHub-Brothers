import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ClientService } from '../../../core/api/client.service';
import { Client } from '../../../core/models/client.model';

/** Same rules as the backend's CreateClientRequest (BCrypt ignores bytes past 72). */
const LETTERS_ONLY = /^[a-zA-Z]+$/;
export const PASSWORD_MIN = 12;
const PASSWORD_MAX = 72;

/** Creates a client; closes with the created {@link Client}, or nothing if cancelled. */
@Component({
  selector: 'app-new-client-dialog',
  imports: [
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
  ],
  templateUrl: './new-client-dialog.html',
  styleUrl: './new-client-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NewClientDialog {
  private readonly clientService = inject(ClientService);
  private readonly dialogRef = inject<MatDialogRef<NewClientDialog, Client>>(MatDialogRef);

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
    this.dialogRef.disableClose = true;
    this.clientService.createClient(this.form.getRawValue()).subscribe({
      next: (client) => this.dialogRef.close(client),
      error: (err: unknown) => {
        this.submitting.set(false);
        this.dialogRef.disableClose = false;
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
    if (err.status === 409) return 'Ya existe un cliente con ese email.';
    if (err.status === 400) return 'Revisá los datos del formulario.';
    if (err.status === 403) return 'No tenés permisos para crear clientes.';
  }
  return 'No pudimos crear el cliente. Intentá de nuevo más tarde.';
}
