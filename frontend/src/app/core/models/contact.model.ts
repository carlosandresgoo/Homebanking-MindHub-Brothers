/** Mirrors backend `ContactDTO`: a saved recipient. */
export interface Contact {
  id: number;
  alias: string;
  accountNumber: string;
  /** Masked holder name, e.g. "Lucía P.". */
  holderDisplay: string;
  createdAt: string;
  /** Large transfers to it need no 2FA code (set with a code, see `ContactService.trust`). */
  trusted: boolean;
}

/** Same rule as the backend (`ContactAlias.PATTERN`). */
export const CONTACT_ALIAS_PATTERN = /^\s*[\p{L}\p{N}][\p{L}\p{N} ._\-()]*$/u;
export const CONTACT_ALIAS_MAX = 40;
