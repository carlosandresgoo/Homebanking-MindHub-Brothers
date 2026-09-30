import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, filter, switchMap } from 'rxjs';

import { ContactService } from '../../core/api/contact.service';
import { Contact } from '../../core/models/contact.model';
import { toLoadState } from '../../core/utils/load-state';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { ContactDialog, ContactDialogData } from './contact-dialog/contact-dialog';
import { TrustDialog, TrustDialogData } from './trust-dialog/trust-dialog';

/** Saved recipients: add, rename, delete and transfer to them. */
@Component({
  selector: 'app-contacts',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
    RouterLink,
  ],
  templateUrl: './contacts.html',
  styleUrl: './contacts.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Contacts {
  private readonly contactService = inject(ContactService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly state = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.contactService.getMine()))),
    { requireSync: true },
  );
  protected readonly query = signal('');

  protected readonly visible = computed(() => {
    const s = this.state();
    if (s.status !== 'loaded') return [];
    const q = this.query().trim().toLowerCase();
    return q
      ? s.data.filter((c) =>
          `${c.alias} ${c.holderDisplay} ${c.accountNumber}`.toLowerCase().includes(q),
        )
      : s.data;
  });

  protected retry(): void {
    this.reload$.next();
  }

  protected add(): void {
    this.openDialog({}, (saved) => `Agregaste a ${saved.alias}.`);
  }

  protected edit(contact: Contact): void {
    this.openDialog({ contact }, (saved) => `Ahora se llama ${saved.alias}.`);
  }

  protected remove(contact: Contact): void {
    const data: ConfirmDialogData = {
      title: `Borrar a ${contact.alias}`,
      message: `Vas a quitar la cuenta ${contact.accountNumber} de tu agenda. Podés volver a agregarla cuando quieras.`,
      confirmLabel: 'Borrar',
      icon: 'person_remove',
      danger: true,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => this.contactService.delete(contact.id)),
      )
      .subscribe({
        next: () => {
          this.snackBar.open(`Borraste a ${contact.alias}.`, 'OK');
          this.reload$.next();
        },
        error: (err: unknown) => {
          // Already gone (e.g. deleted in another tab): just refresh.
          if (err instanceof HttpErrorResponse && err.status === 404) this.reload$.next();
          else this.snackBar.open('No pudimos borrarlo. Intentá de nuevo.', 'OK');
        },
      });
  }

  protected trust(contact: Contact): void {
    this.dialog
      .open<TrustDialog, TrustDialogData, Contact>(TrustDialog, {
        data: { contact },
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((saved): saved is Contact => !!saved))
      .subscribe((saved) => {
        this.snackBar.open(`${saved.alias} ahora es de confianza.`, 'OK');
        this.reload$.next();
      });
  }

  /** Makes transfers safer again, so it needs no code or confirmation. */
  protected untrust(contact: Contact): void {
    this.contactService.untrust(contact.id).subscribe({
      next: () => {
        this.snackBar.open(`${contact.alias} ya no es de confianza.`, 'OK');
        this.reload$.next();
      },
      error: () => this.snackBar.open('No pudimos quitarle la confianza. Intentá de nuevo.', 'OK'),
    });
  }

  private openDialog(data: ContactDialogData, message: (saved: Contact) => string): void {
    this.dialog
      .open<ContactDialog, ContactDialogData, Contact>(ContactDialog, { data, maxWidth: '95vw' })
      .afterClosed()
      .pipe(filter((saved): saved is Contact => !!saved))
      .subscribe((saved) => {
        this.snackBar.open(message(saved), 'OK');
        this.reload$.next();
      });
  }
}
