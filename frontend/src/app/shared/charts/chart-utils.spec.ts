import { compactMoney, niceScale } from './chart-utils';

describe('niceScale', () => {
  it('rounds the axis up to a readable step', () => {
    expect(niceScale(7320)).toEqual({ max: 8000, step: 2000 });
    expect(niceScale(37000)).toEqual({ max: 40000, step: 10000 });
    expect(niceScale(900)).toEqual({ max: 1000, step: 250 });
    // Only the steps needed: 41k fits under 60k, not 80k.
    expect(niceScale(41000)).toEqual({ max: 60000, step: 20000 });
  });

  it('handles an empty chart', () => {
    expect(niceScale(0)).toEqual({ max: 4, step: 1 });
  });
});

describe('compactMoney', () => {
  it('abbreviates large amounts in pesos', () => {
    // The suffix ("mil" / "k") depends on the runtime's locale data.
    expect(compactMoney(12500)).toMatch(/\$\s*12,5\s*\S+/);
    expect(compactMoney(0)).toMatch(/\$\s*0/);
  });
});
