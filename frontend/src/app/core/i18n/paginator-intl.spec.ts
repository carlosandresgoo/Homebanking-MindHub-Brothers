import { SpanishPaginatorIntl } from './paginator-intl';

describe('SpanishPaginatorIntl', () => {
  const intl = new SpanishPaginatorIntl();

  it('uses Spanish labels', () => {
    expect(intl.itemsPerPageLabel).toBe('Filas por página');
  });

  it('formats the range', () => {
    expect(intl.getRangeLabel(0, 10, 2)).toBe('1 – 2 de 2');
    expect(intl.getRangeLabel(1, 10, 25)).toBe('11 – 20 de 25');
    expect(intl.getRangeLabel(0, 10, 0)).toBe('0 de 0');
  });
});
