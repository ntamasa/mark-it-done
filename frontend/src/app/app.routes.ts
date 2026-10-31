import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Log in',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Create account',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'forgot-password',
    title: 'Forgot password',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    title: 'Reset password',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: '',
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'week', pathMatch: 'full' },
      { path: 'week', title: 'Week', loadComponent: () => import('./features/week/week').then((m) => m.WeekPage) },
      {
        path: 'two-days',
        title: '2 days',
        loadComponent: () => import('./features/two-days/two-days').then((m) => m.TwoDaysPage),
      },
      {
        path: 'recurring',
        title: 'Recurring',
        loadComponent: () => import('./features/recurring/recurring').then((m) => m.RecurringPage),
      },
      {
        path: 'categories',
        title: 'Categories',
        loadComponent: () => import('./features/categories/categories').then((m) => m.CategoriesPage),
      },
      { path: '**', redirectTo: 'week' },
    ],
  },
];
