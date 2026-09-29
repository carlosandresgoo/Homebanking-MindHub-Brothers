export type Role = 'CLIENT' | 'ADMIN';

/** Mirrors backend `com.mindhub.homebanking.dto.TokenResponse`. */
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds until the access token expires. */
  expiresIn: number;
  role: Role;
}

/** Mirrors backend `com.mindhub.homebanking.dto.LoginRequest`. */
export interface LoginRequest {
  email: string;
  password: string;
  /** Authenticator code, only for clients with 2FA enabled. */
  secondFactorCode?: string;
}

/**
 * The API answers 403 with `secondFactor` when an operation needs an authenticator code:
 * `REQUIRED` (ask for one) or `INVALID` (wrong, expired or already used).
 */
export type SecondFactorProblem = 'REQUIRED' | 'INVALID';

export function secondFactorProblem(err: unknown): SecondFactorProblem | null {
  const e = err as { status?: number; error?: { secondFactor?: string } } | null;
  const value = e?.status === 403 ? e.error?.secondFactor : undefined;
  return value === 'REQUIRED' || value === 'INVALID' ? value : null;
}
