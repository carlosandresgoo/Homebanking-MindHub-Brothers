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
    path: 'register',
    title: 'Abrí tu cuenta | MindHub Brothers',
    loadComponent: () => import('./features/register/register').then((m) => m.Register),
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
        path: 'accounts/:id',
        title: 'Detalle de cuenta | MindHub Brothers',
        loadComponent: () =>
          import('./features/account-detail/account-detail').then((m) => m.AccountDetailPage),
      },
      {
        path: 'cards',
        title: 'Mis tarjetas | MindHub Brothers',
        canActivate: [roleGuard('CLIENT')],
        loadComponent: () => import('./features/cards/cards').then((m) => m.Cards),
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
