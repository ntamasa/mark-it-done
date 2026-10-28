import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';
import { WeekPlaceholder } from './features/week/week-placeholder';

export const routes: Routes = [
  { path: 'week', component: WeekPlaceholder, canActivate: [authGuard] },
  // Placeholders until the auth pages exist; they prevent a /login <-> /week redirect loop.
  { path: 'login', component: WeekPlaceholder, canActivate: [guestGuard] },
  { path: 'register', component: WeekPlaceholder, canActivate: [guestGuard] },
  { path: '', redirectTo: 'week', pathMatch: 'full' },
  { path: '**', redirectTo: 'week' },
];
