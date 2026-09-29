import { TransactionCategory } from './account.model';

/** Mirrors backend `FinancialSummaryDTO` (own-account transfers are not income nor expense). */
export interface FinancialSummary {
  totalBalance: number;
  /** Oldest first; `month` is the first day (`yyyy-MM-dd`). */
  months: { month: string; income: number; expense: number }[];
  /** Largest first. */
  expenses: { category: TransactionCategory; amount: number }[];
  /** Total balance at the end of each day, oldest first. */
  balanceHistory: { date: string; balance: number }[];
}
