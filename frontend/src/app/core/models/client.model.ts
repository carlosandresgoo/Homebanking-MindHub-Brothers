import { Account } from './account.model';

/** Mirrors backend `com.mindhub.homebanking.dto.ClientDTO`. */
export interface Client {
  id: number;
  name: string;
  lastName: string;
  email: string;
  accounts: Account[];
}
