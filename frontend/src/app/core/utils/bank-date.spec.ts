import { bankDate } from './bank-date';

describe('bankDate', () => {
  it('uses the bank time zone, not the browser one', () => {
    // 23:30 on the 29th in Bogotá (UTC-5) is already 01:30 on the 30th in Buenos Aires (UTC-3).
    const lateNight = new Date('2026-09-30T04:30:00Z');
    expect(bankDate(0, lateNight)).toBe('2026-09-30');
    expect(bankDate(1, lateNight)).toBe('2026-10-01');
  });

  it('crosses months and years', () => {
    expect(bankDate(1, new Date('2026-12-31T15:00:00Z'))).toBe('2027-01-01');
  });
});
