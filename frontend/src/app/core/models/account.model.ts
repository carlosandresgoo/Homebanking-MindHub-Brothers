/** Mirrors backend `com.mindhub.homebanking.dto.AccountDTO`. */
export interface Account {
  id: number;
  number: string;
  /** ISO-8601 local date-time, e.g. `2026-09-29T10:24:55.635622`. */
  creationDate: string;
  balance: number;
}
