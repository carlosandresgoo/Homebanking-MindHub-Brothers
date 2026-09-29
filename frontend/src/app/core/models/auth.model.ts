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
}
