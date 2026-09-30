import { formatCbu, shareText } from './cbu';

describe('cbu utils', () => {
  it('splits the CBU into its two blocks', () => {
    expect(formatCbu('9990001800000000000017')).toBe('99900018 00000000000017');
    expect(formatCbu('123')).toBe('123');
  });

  it('builds the text to share the account data', () => {
    const text = shareText({
      number: 'VIN001',
      cbu: '9990001800000000000017',
      alias: 'melba.ahorros',
    });
    expect(text).toContain('CBU: 9990001800000000000017');
    expect(text).toContain('Alias: melba.ahorros');
    expect(text.split('\n')[0]).toBe('Banco: MindHub Brothers');
  });
});
