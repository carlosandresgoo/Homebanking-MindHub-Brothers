import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, filter, forkJoin, switchMap } from 'rxjs';

import { CardService } from '../../core/api/card.service';
import { ClientService } from '../../core/api/client.service';
import {
  CARD_COLORS,
  CARD_COLOR_LABEL,
  CARD_TYPES,
  CARD_TYPE_LABEL,
  Card,
  CardType,
} from '../../core/models/card.model';
import { toLoadState } from '../../core/utils/load-state';
import { BankCard } from '../../shared/bank-card/bank-card';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import {
  RequestCardDialog,
  RequestCardDialogData,
} from './request-card-dialog/request-card-dialog';

const MAX_CARDS = CARD_TYPES.length * CARD_COLORS.length;

@Component({
  selector: 'app-cards',
  imports: [BankCard, DatePipe, MatButtonModule, MatIconModule, MatProgressBarModule],
  templateUrl: './cards.html',
  styleUrl: './cards.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Cards {
  private readonly cardService = inject(CardService);
  private readonly clientService = inject(ClientService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly typeLabel = CARD_TYPE_LABEL;
  protected readonly types = CARD_TYPES;

  protected readonly state = toSignal(
    this.reload$.pipe(
      switchMap(() =>
        toLoadState(
          forkJoin({
            cards: this.cardService.getMyCards(),
            client: this.clientService.getCurrentClient(),
          }),
        ),
      ),
    ),
    { requireSync: true },
  );

  protected readonly cards = computed(() => {
    const s = this.state();
    return s.status === 'loaded' ? s.data.cards : [];
  });

  protected readonly canRequest = computed(() => this.cards().length < MAX_CARDS);

  protected cardsOf(type: CardType): Card[] {
    return this.cards().filter((card) => card.type === type);
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected requestCard(): void {
    const s = this.state();
    if (s.status !== 'loaded') return;
    const data: RequestCardDialogData = {
      existing: s.data.cards,
      cardholder: `${s.data.client.name} ${s.data.client.lastName}`,
    };
    this.dialog
      .open<RequestCardDialog, RequestCardDialogData, boolean>(RequestCardDialog, {
        data,
        width: '480px',
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() => this.reload$.next());
  }

  protected deactivate(card: Card): void {
    const name = `${CARD_TYPE_LABEL[card.type].toLowerCase()} ${CARD_COLOR_LABEL[card.color]}`;
    const data: ConfirmDialogData = {
      title: 'Desactivar tarjeta',
      message: `La tarjeta de ${name} terminada en ${card.last4} dejará de funcionar. No se puede reactivar, pero podés pedir una nueva.`,
      confirmLabel: 'Desactivar',
      icon: 'credit_card_off',
      danger: true,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(
        filter(Boolean),
        switchMap(() => this.cardService.deactivateCard(card.id)),
      )
      .subscribe({
        next: () => {
          this.snackBar.open(`Desactivaste la tarjeta terminada en ${card.last4}.`, 'OK');
          this.reload$.next();
        },
        error: () =>
          this.snackBar.open('No pudimos desactivar la tarjeta. Intentá de nuevo.', 'OK'),
      });
  }
}
