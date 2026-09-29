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
  accounts: Account[];
}

/** Mirrors backend `com.mindhub.homebanking.dto.CreateClientRequest`. */
export interface CreateClientRequest {
  name: string;
  lastName: string;
  email: string;
  password: string;
}
