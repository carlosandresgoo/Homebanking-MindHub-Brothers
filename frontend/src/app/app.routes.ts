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
    path: 'forgot-password',
    title: 'Restablecer contraseña | MindHub Brothers',
    loadComponent: () =>
      import('./features/password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    title: 'Nueva contraseña | MindHub Brothers',
    loadComponent: () => import('./features/password/reset-password').then((m) => m.ResetPassword),
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
        path: 'movements/:id',
        title: 'Comprobante | MindHub Brothers',
        loadComponent: () => import('./features/receipt/receipt').then((m) => m.Receipt),
      },
      {
        path: 'cards',
        title: 'Mis tarjetas | MindHub Brothers',
        canActivate: [roleGuard('CLIENT')],
        loadComponent: () => import('./features/cards/cards').then((m) => m.Cards),
      },
      {
        path: 'transfers',
        title: 'Transferir | MindHub Brothers',
        canActivate: [roleGuard('CLIENT')],
        loadComponent: () => import('./features/transfers/transfers').then((m) => m.Transfers),
      },
      {
        path: 'contacts',
        title: 'Destinatarios | MindHub Brothers',
        canActivate: [roleGuard('CLIENT')],
        loadComponent: () => import('./features/contacts/contacts').then((m) => m.Contacts),
      },
      {
        path: 'loans',
        title: 'Préstamos | MindHub Brothers',
        canActivate: [roleGuard('CLIENT')],
        loadComponent: () => import('./features/loans/loans').then((m) => m.Loans),
      },
      {
        path: 'audit',
        title: 'Auditoría | MindHub Brothers',
        canActivate: [roleGuard('ADMIN')],
        loadComponent: () => import('./features/audit/audit').then((m) => m.Audit),
      },
      {
        path: 'profile',
        title: 'Mi perfil | MindHub Brothers',
        loadComponent: () => import('./features/profile/profile').then((m) => m.Profile),
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
