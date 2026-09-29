import { fileNameFrom } from './download';

describe('fileNameFrom', () => {
  it('reads quoted and unquoted file names', () => {
    expect(fileNameFrom('attachment; filename="movimientos-VIN001.csv"')).toBe(
      'movimientos-VIN001.csv',
    );
    expect(fileNameFrom('attachment; filename=a.csv')).toBe('a.csv');
  });

  it('never returns a path', () => {
    expect(fileNameFrom('attachment; filename="../../evil.csv"')).toBe('.._.._evil.csv');
  });

  it('returns null without a file name', () => {
    expect(fileNameFrom(null)).toBeNull();
    expect(fileNameFrom('inline')).toBeNull();
  });
});
