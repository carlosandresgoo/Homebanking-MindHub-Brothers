/** "9990001800000000000017" → "99900018 00000000000017" (bank block + account block). */
export function formatCbu(cbu: string): string {
  return cbu.length === 22 ? `${cbu.slice(0, 8)} ${cbu.slice(8)}` : cbu;
}

/** Text to share the data needed to receive a transfer (the sender sees the holder when looking it up). */
export function shareText(account: { number: string; cbu: string; alias: string }): string {
  return [
    'Banco: MindHub Brothers',
    `CBU: ${account.cbu}`,
    `Alias: ${account.alias}`,
    `Cuenta: ${account.number}`,
  ].join('\n');
}
