import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ThemePreference, User } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

function user(themePreference: ThemePreference): User {
  return { id: '1', email: 'a@b.c', displayName: 'A', timezone: 'UTC', themePreference };
}

describe('ThemeService', () => {
  let osDark: boolean;
  let listener: ((e: { matches: boolean }) => void) | null;
  let userSignal: ReturnType<typeof signal<User | null>>;
  let updateMe: ReturnType<typeof vi.fn>;

  function create(): ThemeService {
    const svc = TestBed.inject(ThemeService);
    TestBed.tick();
    return svc;
  }

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('app-dark');
    osDark = false;
    listener = null;
    window.matchMedia = vi.fn().mockImplementation(() => ({
      get matches() {
        return osDark;
      },
      addEventListener: (_: string, cb: (e: { matches: boolean }) => void) => (listener = cb),
      removeEventListener: vi.fn(),
    })) as unknown as typeof window.matchMedia;

    userSignal = signal<User | null>(null);
    updateMe = vi.fn().mockReturnValue(of(user('DARK')));
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { user: userSignal.asReadonly(), isLoggedIn: () => userSignal() !== null, updateMe },
        },
      ],
    });
  });

  const isDarkClass = () => document.documentElement.classList.contains('app-dark');

  it('defaults to SYSTEM and follows the OS setting', () => {
    const svc = create();
    expect(svc.preference()).toBe('SYSTEM');
    expect(isDarkClass()).toBe(false);
  });

  it('DARK adds the app-dark class and LIGHT removes it', () => {
    const svc = create();
    svc.set('DARK');
    TestBed.tick();
    expect(isDarkClass()).toBe(true);
    svc.set('LIGHT');
    TestBed.tick();
    expect(isDarkClass()).toBe(false);
  });

  it('SYSTEM follows a media change event', () => {
    const svc = create();
    expect(svc.isDark()).toBe(false);
    listener?.({ matches: true });
    TestBed.tick();
    expect(isDarkClass()).toBe(true);
    listener?.({ matches: false });
    TestBed.tick();
    expect(isDarkClass()).toBe(false);
  });

  it('reads the cached preference and ignores garbage', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'DARK');
    expect(create().preference()).toBe('DARK');
  });

  it('falls back to SYSTEM when the cache holds an invalid value', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'PINK');
    expect(create().preference()).toBe('SYSTEM');
  });

  it('caches the choice', () => {
    create().set('LIGHT');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('LIGHT');
  });

  it('cycles SYSTEM -> LIGHT -> DARK -> SYSTEM', () => {
    const svc = create();
    const seen: ThemePreference[] = [];
    for (let i = 0; i < 3; i++) {
      svc.cycle();
      seen.push(svc.preference());
    }
    expect(seen).toEqual(['LIGHT', 'DARK', 'SYSTEM']);
  });

  it('saves to the server only when logged in', () => {
    const svc = create();
    svc.set('DARK');
    expect(updateMe).not.toHaveBeenCalled();
    userSignal.set(user('DARK'));
    svc.set('LIGHT');
    expect(updateMe).toHaveBeenCalledWith({ themePreference: 'LIGHT' });
  });

  it('keeps the local choice when saving fails', () => {
    userSignal.set(user('SYSTEM'));
    updateMe.mockReturnValue(throwError(() => new Error('boom')));
    const svc = create();
    svc.set('DARK');
    expect(svc.preference()).toBe('DARK');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('DARK');
  });

  it('the server preference overrides the cache on login', () => {
    localStorage.setItem(THEME_STORAGE_KEY, 'LIGHT');
    const svc = create();
    userSignal.set(user('DARK'));
    TestBed.tick();
    expect(svc.preference()).toBe('DARK');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('DARK');
    expect(isDarkClass()).toBe(true);
  });

  it('the server preference does not override a choice made in this session', () => {
    const svc = create();
    svc.set('LIGHT');
    userSignal.set(user('DARK'));
    TestBed.tick();
    expect(svc.preference()).toBe('LIGHT');
  });

  it('pushes a session choice made while logged out to the server after login', () => {
    const svc = create();
    svc.set('LIGHT');
    expect(updateMe).not.toHaveBeenCalled();
    userSignal.set(user('DARK'));
    TestBed.tick();
    expect(svc.preference()).toBe('LIGHT');
    expect(updateMe).toHaveBeenCalledTimes(1);
    expect(updateMe).toHaveBeenCalledWith({ themePreference: 'LIGHT' });
  });

  it('does not PATCH after login when no choice was made or it equals the server value', () => {
    const svc = create();
    userSignal.set(user('DARK'));
    TestBed.tick();
    expect(updateMe).not.toHaveBeenCalled();
    expect(svc.preference()).toBe('DARK');

    userSignal.set(null);
    TestBed.tick();
    svc.set('LIGHT');
    userSignal.set(user('LIGHT'));
    TestBed.tick();
    expect(updateMe).not.toHaveBeenCalled();
  });
});
