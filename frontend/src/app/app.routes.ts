import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'accounts',
    title: 'Account | Banking',
    loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
  },
  // Pilot phase: only `accounts` is migrated; home and manager come next.
  { path: '', pathMatch: 'full', redirectTo: 'accounts' },
  { path: '**', redirectTo: 'accounts' },
];
