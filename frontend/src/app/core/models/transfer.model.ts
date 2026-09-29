/** Mirrors backend `TransferRequest`. */
export interface TransferRequest {
  sourceAccountNumber: string;
  targetAccountNumber: string;
  amount: number;
  description?: string;
  /** Authenticator code, for large transfers to others when 2FA is enabled. */
  secondFactorCode?: string;
}

/** Mirrors backend `TransferLimitsDTO`: today's allowance for transfers to other clients. */
export interface TransferLimits {
  dailyLimit: number;
  usedToday: number;
  remainingToday: number;
  secondFactorEnabled: boolean;
  /** With 2FA enabled, transfers of this amount or more need a code. */
  secondFactorThreshold: number;
  /** The daily limit the client gets by enabling 2FA. */
  limitWithSecondFactor: number;
}

/** Mirrors backend `TransferReceiptDTO` (sender's view: no destination balance or owner). */
export interface TransferReceipt {
  transactionId: number;
  sourceAccountId: number;
  sourceAccountNumber: string;
  targetAccountNumber: string;
  amount: number;
  description: string;
  date: string;
  sourceBalanceAfter: number;
}
