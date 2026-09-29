import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'MindHub Brothers — Tu banca online',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    title: 'Ingresar | MindHub Brothers',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    // Authenticated area: toolbar + user menu around the private pages.
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: 'accounts',
        title: 'Mis cuentas | MindHub Brothers',
        loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
      },
      {
        path: 'manager',
        title: 'Clientes | MindHub Brothers',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/manager/manager').then((m) => m.Manager),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
