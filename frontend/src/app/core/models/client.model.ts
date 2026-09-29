import { Account } from './account.model';
import { Role } from './auth.model';

/** Mirrors backend `com.mindhub.homebanking.dto.ClientDTO`. */
export interface Client {
  id: number;
  name: string;
  lastName: string;
  email: string;
  role: Role;
  /** false when blocked by an administrator. */
  enabled?: boolean;
  /** true while temporarily locked after too many failed logins. */
  locked?: boolean;
  /** true when sign-in and large transfers need an authenticator app code. */
  twoFactorEnabled?: boolean;
  accounts: Account[];
}

/** Mirrors backend `TwoFactorSetupDTO`: shown once while enrolling. */
export interface TwoFactorSetup {
  /** Base32, for manual entry in the app. */
  secret: string;
  /** otpauth:// URI, rendered as a QR code. */
  otpauthUri: string;
}

/** Mirrors backend `com.mindhub.homebanking.dto.CreateClientRequest`. */
export interface CreateClientRequest {
  name: string;
  lastName: string;
  email: string;
  password: string;
}
