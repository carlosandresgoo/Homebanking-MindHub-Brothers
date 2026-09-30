import { Account } from '../core/models/account.model';
import { ClientLoan, Loan } from '../core/models/loan.model';

export const CATALOG: Loan[] = [
  {
    id: 1,
    code: 'MORTGAGE',
    name: 'Hipotecario',
    maxAmount: 500000,
    interestRate: 0.2,
    payments: [12, 24, 36, 48, 60],
  },
  {
    id: 2,
    code: 'PERSONAL',
    name: 'Personal',
    maxAmount: 100000,
    interestRate: 0.2,
    payments: [6, 12, 24],
  },
  {
    id: 3,
    code: 'AUTOMOTIVE',
    name: 'Automotor',
    maxAmount: 300000,
    interestRate: 0.2,
    payments: [6, 12, 24, 36],
  },
];

export const ACCOUNTS: Account[] = [
  {
    id: 11,
    number: 'VIN001',
    cbu: '9990001800000000000017',
    alias: 'vin001.test',
    creationDate: '2026-08-30T00:00:00',
    balance: 5000,
  },
  {
    id: 12,
    number: 'VIN002',
    cbu: '9990001800000000000017',
    alias: 'vin002.test',
    creationDate: '2026-08-31T00:00:00',
    balance: 1000,
  },
];

export const PERSONAL_LOAN: ClientLoan = {
  id: 7,
  loanId: 2,
  code: 'PERSONAL',
  name: 'Personal',
  amount: 30000,
  totalDue: 36000,
  payments: 12,
  paymentsMade: 2,
  nextInstallment: 3000,
  outstanding: 30000,
  paidOff: false,
  createdAt: '2026-09-09T10:00:00',
};
