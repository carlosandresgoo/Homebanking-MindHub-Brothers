/** Mirrors backend `com.mindhub.homebanking.dto.AccountDTO`. */
export interface Account {
  id: number;
  number: string;
  /** ISO-8601 local date-time, e.g. `2026-09-29T10:24:55.635622`. */
  creationDate: string;
  balance: number;
}

export type TransactionType = 'CREDIT' | 'DEBIT';

/** Mirrors backend `com.mindhub.homebanking.domain.TransactionCategory`. */
export type TransactionCategory =
  'DEPOSIT' | 'TRANSFER_OUT' | 'TRANSFER_IN' | 'LOAN_DISBURSEMENT' | 'LOAN_PAYMENT' | 'OTHER';

/** Mirrors backend `com.mindhub.homebanking.dto.TransactionDTO`. */
export interface Transaction {
  id: number;
  type: TransactionType;
  category?: TransactionCategory;
  amount: number;
  description: string;
  date: string;
  balanceAfter: number;
}

/** Mirrors backend `AccountDetailDTO`; its movements come paginated from `MovementService`. */
export type AccountDetail = Account;

export const CATEGORY_LABEL: Record<TransactionCategory, string> = {
  DEPOSIT: 'Depósito',
  TRANSFER_OUT: 'Transferencia enviada',
  TRANSFER_IN: 'Transferencia recibida',
  LOAN_DISBURSEMENT: 'Préstamo acreditado',
  LOAN_PAYMENT: 'Cuota de préstamo',
  OTHER: 'Otro',
};

/** Filters for an account's movements (all optional). Dates are `yyyy-MM-dd`, both inclusive. */
export interface MovementQuery {
  from?: string;
  to?: string;
  type?: TransactionType;
  category?: TransactionCategory;
  /** Text contained in the description. */
  q?: string;
}

/** Mirrors backend `MovementReceiptDTO`. */
export interface MovementReceipt {
  id: number;
  accountId: number;
  accountNumber: string;
  accountHolder: string;
  type: TransactionType;
  category: TransactionCategory;
  amount: number;
  description: string;
  date: string;
  balanceAfter: number;
  /** For transfers: the other account and its masked holder. */
  counterparty: string | null;
  counterpartyHolder: string | null;
}

/** Same limit as the backend (`AccountService.MAX_ACTIVE_ACCOUNTS`). */
export const MAX_ACTIVE_ACCOUNTS = 3;
