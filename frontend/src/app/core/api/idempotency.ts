import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

/**
 * A new key identifies one logical operation (a transfer, a loan payment...). Reuse the same key when
 * the user retries that operation, so the API returns the original result instead of repeating it.
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function idempotencyHeaders(key: string | undefined): HttpHeaders | undefined {
  return key ? new HttpHeaders({ 'Idempotency-Key': key }) : undefined;
}

/** No response, or a server error: we cannot know whether the operation was applied. */
export function isOutcomeUnknown(err: unknown): boolean {
  return !(err instanceof HttpErrorResponse) || err.status === 0 || err.status >= 500;
}

/**
 * Hands out the Idempotency-Key for a screen's write operation. Sending the same request again reuses
 * the key until the operation is {@link settle settled} (success or a definitive 4xx answer); a
 * different request always gets a new key, so the API never rejects it as a mismatched replay.
 */
export class IdempotentOperation {
  private key = newIdempotencyKey();
  private fingerprint: string | undefined;

  keyFor(request: unknown): string {
    const fingerprint = JSON.stringify(request ?? null);
    if (fingerprint !== this.fingerprint) {
      this.key = newIdempotencyKey();
      this.fingerprint = fingerprint;
    }
    return this.key;
  }

  /** The server gave a definitive answer: the next request is a new operation. */
  settle(): void {
    this.fingerprint = undefined;
  }

  /** Settles unless the outcome of `err` is unknown (then a retry must reuse the key). */
  settleUnlessUnknown(err: unknown): void {
    if (!isOutcomeUnknown(err)) this.settle();
  }
}
