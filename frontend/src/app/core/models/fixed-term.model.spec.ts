import { addDays, fixedTermInterest } from './fixed-term.model';

describe('fixedTermInterest', () => {
  it('matches the backend: principal × TNA × days / 365, to cents', () => {
    expect(fixedTermInterest(1000, 0.35, 30)).toBe(28.77);
    expect(fixedTermInterest(2000, 0.35, 30)).toBe(57.53);
    expect(fixedTermInterest(100000, 0.4, 365)).toBe(40000);
  });

  it('rounds exact halves to the even cent', () => {
    // 365 × 0.01 × 1 day / 365 = 0.005 → 0.00 (half-even), 0.015 → 0.02
    expect(fixedTermInterest(36.5, 0.05, 1)).toBe(0);
    expect(fixedTermInterest(109.5, 0.05, 1)).toBe(0.02);
  });
});

describe('addDays', () => {
  it('adds days across months and years', () => {
    expect(addDays('2026-09-29', 30)).toBe('2026-10-29');
    expect(addDays('2026-12-15', 30)).toBe('2027-01-14');
  });
});
