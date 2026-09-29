import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Home | MindHub Banking',
    loadComponent: () => import('./features/home/home').then((m) => m.Home),
  },
  {
    path: 'accounts',
    title: 'Account | Banking',
    loadComponent: () => import('./features/accounts/accounts').then((m) => m.Accounts),
  },
  {
    path: 'manager',
    title: 'Banking',
    loadComponent: () => import('./features/manager/manager').then((m) => m.Manager),
  },
  { path: '**', redirectTo: '' },
];
