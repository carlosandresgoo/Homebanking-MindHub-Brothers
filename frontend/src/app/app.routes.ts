import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Home | MindHub Banking',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    title: 'Sign on | MindHub Banking',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'accounts',
    title: 'Account | Banking',
    canActivate: [authGuard],
    loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
  },
  {
    path: 'manager',
    title: 'Banking',
    canActivate: [authGuard, roleGuard('ADMIN')],
    loadComponent: () => import('./features/manager/manager').then((m) => m.Manager),
  },
  { path: '**', redirectTo: '' },
];
