import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';
import { WeekPlaceholder } from './features/week/week-placeholder';

export const routes: Routes = [
  { path: 'week', component: WeekPlaceholder, canActivate: [authGuard] },
  // Placeholder until the login page exists (MAR-59); it prevents a /login <-> /week redirect loop.
  { path: 'login', component: WeekPlaceholder, canActivate: [guestGuard] },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  { path: '', redirectTo: 'week', pathMatch: 'full' },
  { path: '**', redirectTo: 'week' },
];
