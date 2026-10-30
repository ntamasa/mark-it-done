import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { User } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { Login, safeReturnUrl } from './login';

const USER: User = { id: '1', email: 'a@b.co', displayName: 'Ann', timezone: 'UTC', themePreference: 'LIGHT' };

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let el: HTMLElement;
  const login = vi.fn();
  let navigateByUrl: ReturnType<typeof vi.spyOn>;
  let returnUrl: string | null;

  async function setup(): Promise<void> {
    login.mockReset();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService,
        { provide: AuthService, useValue: { login, user: () => null, isLoggedIn: () => false } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) } },
        },
      ],
    }).compileComponents();
    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  beforeEach(() => {
    returnUrl = null;
  });

  function setValue(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>('#' + id)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function fillValid(): void {
    setValue('login-email', 'a@b.co');
    setValue('login-password', 'secret');
  }

  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const alerts = () => Array.from(el.querySelectorAll('.field-error')).map((e) => e.textContent!.trim());

  it('shows the required errors on an empty submit and does not call the API', async () => {
    await setup();
    await submit();
    expect(alerts()).toEqual(['Email is required.', 'Password is required.']);
    expect(login).not.toHaveBeenCalled();
  });

  it('shows an invalid email message', async () => {
    await setup();
    setValue('login-email', 'nope');
    await submit();
    expect(alerts()).toContain('Enter a valid email address.');
  });

  it('has no aria-invalid before touch', async () => {
    await setup();
    expect(el.querySelectorAll('[aria-invalid]').length).toBe(0);
  });

  it('logs in with remember me on by default and the time zone, then goes to /week', async () => {
    await setup();
    login.mockReturnValue(of(USER));
    fillValid();
    await submit();
    expect(login).toHaveBeenCalledWith({
      email: 'a@b.co',
      password: 'secret',
      rememberMe: true,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    });
    expect(navigateByUrl).toHaveBeenCalledWith('/week');
  });

  it('sends rememberMe=false when unchecked', async () => {
    await setup();
    login.mockReturnValue(of(USER));
    fillValid();
    const box = el.querySelector<HTMLInputElement>('#login-remember')!;
    box.click();
    fixture.detectChanges();
    await submit();
    expect(login).toHaveBeenCalledWith(expect.objectContaining({ rememberMe: false }));
  });

  it('navigates to a safe returnUrl', async () => {
    returnUrl = '/two-days';
    await setup();
    login.mockReturnValue(of(USER));
    fillValid();
    await submit();
    expect(navigateByUrl).toHaveBeenCalledWith('/two-days');
  });

  it('falls back to /week for an open-redirect returnUrl', async () => {
    returnUrl = '//evil.com';
    await setup();
    login.mockReturnValue(of(USER));
    fillValid();
    await submit();
    expect(navigateByUrl).toHaveBeenCalledWith('/week');
  });

  it('shows the credentials message on 401, keeps the password, and clears it on edit', async () => {
    await setup();
    login.mockReturnValue(
      throwError(() => ({ status: 401, error: { code: 'INVALID_CREDENTIALS', detail: 'x', errors: [] } })),
    );
    fillValid();
    await submit();
    expect(el.querySelector('p-message')!.textContent).toContain('Email or password is incorrect.');
    expect(el.querySelector<HTMLInputElement>('#login-password')!.value).toBe('secret');
    expect(navigateByUrl).not.toHaveBeenCalled();
    setValue('login-password', 'secret2');
    expect(el.querySelector('p-message')).toBeNull();
  });

  it('shows the problem detail for a network error', async () => {
    await setup();
    login.mockReturnValue(throwError(() => ({ status: 0, error: null })));
    fillValid();
    await submit();
    expect(el.querySelector('p-message')!.textContent).toContain('Cannot reach the server');
  });

  it('toggles password visibility', async () => {
    await setup();
    const btn = el.querySelector<HTMLButtonElement>('.eye-btn')!;
    btn.click();
    fixture.detectChanges();
    expect(btn.getAttribute('aria-label')).toBe('Hide password');
    expect(el.querySelector<HTMLInputElement>('#login-password')!.type).toBe('text');
  });
});

describe('safeReturnUrl', () => {
  it.each([
    ['/two-days', '/two-days'],
    ['//evil.com', '/week'],
    ['/\\evil.com', '/week'],
    ['https://evil.com', '/week'],
    ['', '/week'],
    [null, '/week'],
  ])('%s -> %s', (input, expected) => {
    expect(safeReturnUrl(input)).toBe(expected);
  });
});
