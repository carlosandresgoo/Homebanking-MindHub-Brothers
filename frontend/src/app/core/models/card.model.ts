export type CardType = 'CREDIT' | 'DEBIT';
export type CardColor = 'GOLD' | 'SILVER' | 'TITANIUM';

/** Mirrors backend `CardDTO`: masked, never with the full number or CVV. */
export interface Card {
  id: number;
  cardholder: string;
  type: CardType;
  color: CardColor;
  last4: string;
  /** ISO date (yyyy-MM-dd). */
  fromDate: string;
  thruDate: string;
  expired: boolean;
}

/** Mirrors backend `IssueCardRequest`. */
export interface IssueCardRequest {
  type: CardType;
  color: CardColor;
}

/** Mirrors backend `IssuedCardDTO`: the only time the full number and CVV are available. */
export interface IssuedCard {
  card: Card;
  number: string;
  cvv: string;
}

export const CARD_TYPE_LABEL: Record<CardType, string> = { CREDIT: 'Crédito', DEBIT: 'Débito' };
export const CARD_COLOR_LABEL: Record<CardColor, string> = {
  GOLD: 'Gold',
  SILVER: 'Silver',
  TITANIUM: 'Titanium',
};
export const CARD_TYPES: readonly CardType[] = ['DEBIT', 'CREDIT'];
export const CARD_COLORS: readonly CardColor[] = ['SILVER', 'GOLD', 'TITANIUM'];
