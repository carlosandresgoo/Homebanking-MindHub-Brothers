/** Mirrors backend `TransferRequest`. */
export interface TransferRequest {
  sourceAccountNumber: string;
  targetAccountNumber: string;
  amount: number;
  description?: string;
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
