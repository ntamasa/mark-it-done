import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { LoginRequest, RegisterRequest, ThemePreference, User } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly userSignal = signal<User | null>(null);
  readonly user = this.userSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.userSignal() !== null);

  private refreshInFlight: Observable<void> | null = null;

  /** Startup check: CSRF cookie, then current user (one silent refresh attempt). Never redirects. */
  async init(): Promise<void> {
    try {
      await firstValueFrom(this.http.get<void>('/api/auth/csrf'));
    } catch {
      // ignore, start logged out
    }
    const me$ = this.http.get<User>('/api/auth/me');
    const user = await firstValueFrom(
      me$.pipe(
        catchError(() =>
          this.refresh().pipe(
            switchMap(() => me$),
            catchError(() => of(null)),
          ),
        ),
      ),
    );
    this.userSignal.set(user);
    if (user) {
      this.syncTimezone(user);
    }
  }

  /** Silently updates the stored time zone when the browser's differs. Errors are ignored. */
  private syncTimezone(user: User): void {
    let zone: string | undefined;
    try {
      zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch {
      return;
    }
    if (!zone || zone === user.timezone) {
      return;
    }
    this.updateMe({ timezone: zone }).subscribe({ error: () => undefined });
  }

  /** PATCH /api/users/me; on success the user signal is updated with the response. */
  updateMe(patch: { timezone?: string; themePreference?: ThemePreference }): Observable<User> {
    return this.http.patch<User>('/api/users/me', patch).pipe(tap((u) => this.userSignal.set(u)));
  }

  login(req: LoginRequest): Observable<User> {
    return this.http.post<User>('/api/auth/login', req).pipe(tap((u) => this.userSignal.set(u)));
  }

  register(req: RegisterRequest): Observable<User> {
    return this.http.post<User>('/api/auth/register', req).pipe(tap((u) => this.userSignal.set(u)));
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post('/api/auth/forgot-password', { email }).pipe(map(() => undefined));
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>('/api/auth/reset-password', { token, password });
  }

  /** Single-flight: concurrent callers share one refresh request. */
  refresh(): Observable<void> {
    this.refreshInFlight ??= this.http.post<void>('/api/auth/refresh', null).pipe(
      finalize(() => (this.refreshInFlight = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.refreshInFlight;
  }

  logout(): void {
    this.http
      .post<void>('/api/auth/logout', null)
      .pipe(
        catchError(() => of(undefined)),
        finalize(() => this.clearAndRedirect()),
      )
      .subscribe();
  }

  clearAndRedirect(returnUrl?: string): void {
    this.userSignal.set(null);
    void this.router.navigate(['/login'], {
      queryParams: returnUrl ? { returnUrl } : undefined,
    });
  }

  setUser(u: User): void {
    this.userSignal.set(u);
  }
}
