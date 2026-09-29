import { DatePipe, UpperCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import {
  CARD_COLOR_LABEL,
  CARD_TYPE_LABEL,
  CardColor,
  CardType,
} from '../../core/models/card.model';

/**
 * Visual payment card. Shows `•••• 1234` unless `fullNumber` is given (only right after issuing).
 * Decorative text is duplicated for screen readers through `aria-label`.
 */
@Component({
  selector: 'app-bank-card',
  imports: [DatePipe, UpperCasePipe, MatIconModule],
  template: `
    <div class="card" [class]="color().toLowerCase()" role="img" [attr.aria-label]="ariaLabel()">
      <div class="top">
        <span class="brand">MindHub <strong>Brothers</strong></span>
        <span class="type">{{ typeLabel() }} · {{ colorLabel() }}</span>
      </div>
      <div class="middle">
        <span class="chip"></span>
        <mat-icon class="contactless">contactless</mat-icon>
      </div>
      <span class="number">{{ displayNumber() }}</span>
      <div class="bottom">
        <div>
          <small>Titular</small>
          <span>{{ cardholder() | uppercase }}</span>
        </div>
        <div>
          <small>Vence</small>
          <span>{{ thruDate() | date: 'MM/yy' }}</span>
        </div>
        @if (cvv(); as code) {
          <div>
            <small>CVV</small>
            <span>{{ code }}</span>
          </div>
        }
      </div>
      @if (expired()) {
        <span class="expired">Vencida</span>
      }
    </div>
  `,
  styleUrl: './bank-card.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BankCard {
  readonly type = input.required<CardType>();
  readonly color = input.required<CardColor>();
  readonly cardholder = input.required<string>();
  readonly thruDate = input.required<string>();
  readonly last4 = input('');
  readonly fullNumber = input<string>();
  readonly cvv = input<string>();
  readonly expired = input(false);

  protected readonly typeLabel = computed(() => CARD_TYPE_LABEL[this.type()]);
  protected readonly colorLabel = computed(() => CARD_COLOR_LABEL[this.color()]);

  protected readonly displayNumber = computed(() => {
    const full = this.fullNumber();
    return full ? full.replace(/(\d{4})(?=\d)/g, '$1 ') : `•••• •••• •••• ${this.last4()}`;
  });

  protected readonly ariaLabel = computed(
    () =>
      `Tarjeta de ${this.typeLabel().toLowerCase()} ${this.colorLabel()} terminada en ${
        this.fullNumber()?.slice(-4) ?? this.last4()
      }${this.expired() ? ', vencida' : ''}`,
  );
}
