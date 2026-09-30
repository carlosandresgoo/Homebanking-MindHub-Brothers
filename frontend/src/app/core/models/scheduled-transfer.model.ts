export type Frequency = 'ONCE' | 'WEEKLY' | 'MONTHLY';
export type ScheduledStatus = 'ACTIVE' | 'PAUSED' | 'FINISHED' | 'CANCELLED';

/** Mirrors backend `ScheduledTransferDTO`. Dates are `yyyy-MM-dd`. */
export interface ScheduledTransfer {
  id: number;
  sourceAccountId: number;
  sourceAccountNumber: string;
  targetAccountNumber: string;
  /** Masked holder, e.g. "Lucía P.". */
  targetHolder: string;
  amount: number;
  description: string | null;
  frequency: Frequency;
  startDate: string;
  /** Null once finished or cancelled. */
  nextRun: string | null;
  runs: number;
  maxRuns: number | null;
  status: ScheduledStatus;
  lastRunAt: string | null;
  lastOutcome: 'DONE' | 'FAILED' | null;
  /** Why the last run failed, in Spanish. */
  lastError: string | null;
  createdAt: string;
}

/** Mirrors backend `CreateScheduledTransferRequest`. */
export interface CreateScheduledTransferRequest {
  sourceAccountNumber: string;
  /** Account number, CBU or alias. */
  targetAccountNumber: string;
  amount: number;
  description?: string;
  frequency: Frequency;
  startDate: string;
  /** Recurring only: stop after this many; omitted = until cancelled. */
  maxRuns?: number;
  secondFactorCode?: string;
}

export const FREQUENCY_LABEL: Record<Frequency, string> = {
  ONCE: 'Una vez',
  WEEKLY: 'Cada semana',
  MONTHLY: 'Cada mes',
};

export const SCHEDULED_STATUS_LABEL: Record<ScheduledStatus, string> = {
  ACTIVE: 'Activa',
  PAUSED: 'Pausada',
  FINISHED: 'Terminada',
  CANCELLED: 'Cancelada',
};

/** Same limit as the backend (`app.banking.scheduled-transfers.max-open`). */
export const MAX_OPEN_SCHEDULED = 20;
