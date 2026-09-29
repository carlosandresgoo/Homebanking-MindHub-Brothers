import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { CardService } from '../../../core/api/card.service';
import {
  CARD_COLORS,
  CARD_COLOR_LABEL,
  CARD_TYPES,
  CARD_TYPE_LABEL,
  Card,
  CardColor,
  CardType,
  IssuedCard,
} from '../../../core/models/card.model';
import { BankCard } from '../../../shared/bank-card/bank-card';

export interface RequestCardDialogData {
  /** Active cards, to disable combinations the client already has. */
  existing: readonly Card[];
  cardholder: string;
}

/**
 * Two steps: choose type + colour, then show the issued card with its full number and CVV once.
 * Closes with `true` when a card was issued.
 */
@Component({
  selector: 'app-request-card-dialog',
  imports: [
    BankCard,
    MatButtonModule,
    MatButtonToggleModule,
    MatDialogModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './request-card-dialog.html',
  styleUrl: './request-card-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RequestCardDialog {
  private readonly cardService = inject(CardService);
  private readonly dialogRef = inject<MatDialogRef<RequestCardDialog, boolean>>(MatDialogRef);
  protected readonly data = inject<RequestCardDialogData>(MAT_DIALOG_DATA);

  protected readonly types = CARD_TYPES;
  protected readonly colors = CARD_COLORS;
  protected readonly typeLabel = CARD_TYPE_LABEL;
  protected readonly colorLabel = CARD_COLOR_LABEL;

  protected readonly type = signal<CardType>('DEBIT');
  protected readonly color = signal<CardColor>('SILVER');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly issued = signal<IssuedCard | null>(null);

  protected readonly alreadyHas = computed(() =>
    this.data.existing.some((c) => c.type === this.type() && c.color === this.color()),
  );

  protected readonly previewThru = new Date(new Date().getFullYear() + 5, new Date().getMonth(), 1)
    .toISOString()
    .slice(0, 10);

  protected isTaken(type: CardType, color: CardColor): boolean {
    return this.data.existing.some((c) => c.type === type && c.color === color);
  }

  protected submit(): void {
    if (this.alreadyHas()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.dialogRef.disableClose = true;
    this.cardService.issueCard({ type: this.type(), color: this.color() }).subscribe({
      next: (issued) => {
        this.submitting.set(false);
        this.issued.set(issued);
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.dialogRef.disableClose = false;
        this.error.set(
          err instanceof HttpErrorResponse && err.status === 409
            ? 'Ya tenés una tarjeta activa de ese tipo y color.'
            : 'No pudimos emitir la tarjeta. Intentá de nuevo.',
        );
      },
    });
  }

  protected finish(): void {
    this.dialogRef.close(true);
  }
}
