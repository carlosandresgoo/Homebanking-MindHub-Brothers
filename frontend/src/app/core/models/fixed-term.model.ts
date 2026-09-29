/** Mirrors backend `FixedTermPlanDTO`. */
export interface FixedTermPlan {
  termDays: number;
  /** TNA as a fraction: 0.35 = 35 %. */
  annualRate: number;
}

/** Mirrors backend `FixedTermDTO`. */
export interface FixedTerm {
  id: number;
  accountId: number;
  accountNumber: string;
  principal: number;
  annualRate: number;
  termDays: number;
  interest: number;
  /** Principal + interest, paid on `maturityDate`. */
  total: number;
  /** `yyyy-MM-dd`. */
  startDate: string;
  maturityDate: string;
  autoRenew: boolean;
  status: 'ACTIVE' | 'PAID';
  paidAt: string | null;
}

/** Mirrors backend `CreateFixedTermRequest`. */
export interface CreateFixedTermRequest {
  accountNumber: string;
  amount: number;
  termDays: number;
  autoRenew: boolean;
}

/** Same rule as the backend (`app.banking.fixed-terms.min-amount`), for early feedback only. */
export const FIXED_TERM_MIN_AMOUNT = 1000;

/**
 * Estimated interest, as the backend computes it: principal × TNA × days / 365, to cents
 * (half-even). The API response is the authoritative figure.
 */
export function fixedTermInterest(principal: number, annualRate: number, days: number): number {
  const cents = (principal * annualRate * days * 100) / 365;
  const floor = Math.floor(cents);
  const diff = cents - floor;
  const rounded =
    Math.abs(diff - 0.5) < 1e-9 ? (floor % 2 === 0 ? floor : floor + 1) : Math.round(cents);
  return rounded / 100;
}

/** `yyyy-MM-dd` + days, as a local date string. */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  const date = new Date(y, m - 1, d + days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
