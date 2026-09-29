/** Mirrors backend `com.mindhub.homebanking.dto.AccountDTO`. */
export interface Account {
  id: number;
  number: string;
  /** ISO-8601 local date-time, e.g. `2026-09-29T10:24:55.635622`. */
  creationDate: string;
  balance: number;
}

export type TransactionType = 'CREDIT' | 'DEBIT';

/** Mirrors backend `com.mindhub.homebanking.dto.TransactionDTO`. */
export interface Transaction {
  id: number;
  type: TransactionType;
  amount: number;
  description: string;
  date: string;
  balanceAfter: number;
}

/** Mirrors backend `com.mindhub.homebanking.dto.AccountDetailDTO` (movements newest first). */
export interface AccountDetail extends Account {
  transactions: Transaction[];
}

/** Same limit as the backend (`AccountService.MAX_ACTIVE_ACCOUNTS`). */
export const MAX_ACTIVE_ACCOUNTS = 3;
