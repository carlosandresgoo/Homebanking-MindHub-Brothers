/** The bank's time zone (backend `app.banking.zone`): "today" for dates the API checks. */
export const BANK_TIME_ZONE = 'America/Argentina/Buenos_Aires';

/**
 * The bank's date `days` from today, as `yyyy-MM-dd`. Not the browser's: near midnight, or for someone
 * abroad, the two differ, and the API would reject a "tomorrow" that is already today for the bank.
 */
export function bankDate(days = 0, now: Date = new Date()): string {
  // en-CA formats as yyyy-MM-dd.
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: BANK_TIME_ZONE }).format(now);
  const date = new Date(`${today}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
