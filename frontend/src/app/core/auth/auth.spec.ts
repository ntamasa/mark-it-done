import {
  HTTP_INTERCEPTORS,
  HttpClient,
  provideHttpClient,
  withInterceptors,
  withInterceptorsFromDi,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { CredentialsInterceptor } from '../http/credentials.interceptor';
import { authGuard, guestGuard } from './auth.guards';
import { authRefreshInterceptor } from './auth-refresh.interceptor';
import { User } from './auth.models';
import { AuthService } from './auth.service';

const USER: User = {
  id: '1',
  email: 'a@b.c',
  displayName: 'A',
  timezone: 'UTC',
  themePreference: 'LIGHT',
};

describe('auth', () => {
  let http: HttpClient;
  let mock: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptorsFromDi(), withInterceptors([authRefreshInterceptor])),
        provideHttpClientTesting(),
        { provide: HTTP_INTERCEPTORS, useClass: CredentialsInterceptor, multi: true },
      ],
    });
    http = TestBed.inject(HttpClient);
    mock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => mock.verify());

  it('init sets the user from /me', async () => {
    const p = auth.init();
    mock.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    await Promise.resolve();
    await vi.waitFor(() => mock.expectOne('/api/auth/me').flush(USER));
    await p;
    expect(auth.user()).toEqual(USER);
    expect(auth.isLoggedIn()).toBe(true);
  });

  it('init starts logged out without redirect when /me and refresh fail', async () => {
    const p = auth.init();
    mock.expectOne('/api/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    await vi.waitFor(() => mock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' }));
    await vi.waitFor(() =>
      mock.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' }),
    );
    await p;
    expect(auth.user()).toBeNull();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('two parallel 401s cause one refresh and both are retried', () => {
    const results: unknown[] = [];
    http.get('/api/a').subscribe((r) => results.push(r));
    http.get('/api/b').subscribe((r) => results.push(r));
    const unauthorized = { status: 401, statusText: 'Unauthorized' };
    mock.expectOne('/api/a').flush(null, unauthorized);
    mock.expectOne('/api/b').flush(null, unauthorized);
    mock.expectOne('/api/auth/refresh').flush(null, { status: 204, statusText: 'No Content' });
    mock.expectOne('/api/a').flush('A');
    mock.expectOne('/api/b').flush('B');
    expect(results).toEqual(['A', 'B']);
  });

  it('failed refresh redirects with the current URL', () => {
    vi.spyOn(router, 'url', 'get').mockReturnValue('/week?x=1');
    let failed = false;
    http.get('/api/a').subscribe({ error: () => (failed = true) });
    mock.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    mock.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(failed).toBe(true);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/week?x=1' },
    });
  });

  it('401 from login passes through untouched', () => {
    let status = 0;
    http.post('/api/auth/login', {}).subscribe({ error: (e) => (status = e.status) });
    mock.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(status).toBe(401);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('logout ends on /login even if the API fails', () => {
    auth.setUser(USER);
    auth.logout();
    mock.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });
    expect(auth.user()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: undefined });
  });

  it('authGuard redirects logged-out users to /login with returnUrl', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, { url: '/week' } as never));
    expect(router.serializeUrl(result as never)).toBe('/login?returnUrl=%2Fweek');
  });

  it('guestGuard redirects logged-in users to /week', () => {
    auth.setUser(USER);
    const result = TestBed.runInInjectionContext(() => guestGuard({} as never, {} as never));
    expect(router.serializeUrl(result as never)).toBe('/week');
  });
});
