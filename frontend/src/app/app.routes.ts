import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';
import { WeekPlaceholder } from './features/week/week-placeholder';

export const routes: Routes = [
  { path: 'week', component: WeekPlaceholder, canActivate: [authGuard] },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  { path: '', redirectTo: 'week', pathMatch: 'full' },
  { path: '**', redirectTo: 'week' },
];
