export type LoanCode = 'MORTGAGE' | 'PERSONAL' | 'AUTOMOTIVE';

/** Mirrors backend `LoanDTO` (catalog product). */
export interface Loan {
  id: number;
  code: LoanCode;
  name: string;
  maxAmount: number;
  /** e.g. 0.2 = 20%. */
  interestRate: number;
  payments: number[];
}

/** Mirrors backend `ClientLoanDTO`. */
export interface ClientLoan {
  id: number;
  loanId: number;
  code: LoanCode;
  name: string;
  amount: number;
  totalDue: number;
  payments: number;
  paymentsMade: number;
  nextInstallment: number;
  outstanding: number;
  paidOff: boolean;
  createdAt: string;
}

/** Mirrors backend `LoanApplicationRequest`. */
export interface LoanApplication {
  loanId: number;
  amount: number;
  payments: number;
  accountNumber: string;
}

export const LOAN_ICON: Record<LoanCode, string> = {
  MORTGAGE: 'home',
  PERSONAL: 'person',
  AUTOMOTIVE: 'directions_car',
};

/** Same formula as the backend: principal plus flat interest, rounded to cents. */
export function totalWithInterest(amount: number, rate: number): number {
  return Math.round(amount * (1 + rate) * 100) / 100;
}
