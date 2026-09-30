/** Mirrors backend `com.mindhub.homebanking.dto.AccountDTO`. */
export interface Account {
  id: number;
  number: string;
  /** 22 digits (Clave Bancaria Uniforme). */
  cbu: string;
  /** Lower-case alias to receive money, e.g. `sol.rio.mate`. */
  alias: string;
  /** ISO-8601 local date-time, e.g. `2026-09-29T10:24:55.635622`. */
  creationDate: string;
  balance: number;
}

export type TransactionType = 'CREDIT' | 'DEBIT';

/** Mirrors backend `com.mindhub.homebanking.domain.TransactionCategory`. */
export type TransactionCategory =
  | 'DEPOSIT'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'LOAN_DISBURSEMENT'
  | 'LOAN_PAYMENT'
  | 'FIXED_TERM_DEPOSIT'
  | 'FIXED_TERM_PAYOUT'
  | 'FIXED_TERM_INTEREST'
  | 'OTHER';

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
  FIXED_TERM_DEPOSIT: 'Plazo fijo constituido',
  FIXED_TERM_PAYOUT: 'Plazo fijo: capital',
  FIXED_TERM_INTEREST: 'Plazo fijo: intereses',
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

/** Mirrors backend `RecipientDTO`: who receives a transfer, shown before confirming it. */
export interface Recipient {
  accountNumber: string;
  cbu: string;
  alias: string;
  /** Masked holder name, e.g. "Lucía P.". */
  holderDisplay: string;
  bank: string;
  /** One of the caller's own accounts. */
  own: boolean;
}

/** Same rule as the backend (`AccountAlias.PATTERN`): 6-20 letters without accents, digits, `.` or `-`. */
export const ACCOUNT_ALIAS_PATTERN = /^[A-Za-z0-9.-]{6,20}$/;

/** Same limit as the backend (`AccountService.MAX_ACTIVE_ACCOUNTS`). */
export const MAX_ACTIVE_ACCOUNTS = 3;
