import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { User } from '../../core/auth/auth.models';
import { ThemeService } from '../../core/theme/theme.service';
import { TodoEditorService } from '../todo-editor/todo-editor.service';
import { initialsOf, Shell } from './shell';
import { ShellHeader } from './shell-header';

@Component({ template: '' })
class Dummy {}

describe('Shell', () => {
  const user: User = {
    id: '1',
    email: 'a@b.c',
    displayName: 'Ada Lovelace',
    timezone: 'UTC',
    themePreference: 'SYSTEM',
  };
  let desktop$: BehaviorSubject<{ matches: boolean }>;
  let auth: { user: ReturnType<typeof signal<User | null>>; logout: ReturnType<typeof vi.fn> };
  let theme: {
    preference: ReturnType<typeof signal<string>>;
    isDark: ReturnType<typeof signal<boolean>>;
    set: ReturnType<typeof vi.fn>;
  };
  let editor: { openNew: ReturnType<typeof vi.fn> };

  async function setup(matches = false) {
    desktop$ = new BehaviorSubject({ matches });
    auth = { user: signal<User | null>(user), logout: vi.fn() };
    theme = { preference: signal('SYSTEM'), isDark: signal(false), set: vi.fn() };
    editor = { openNew: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'week', component: Dummy },
          { path: 'two-days', component: Dummy },
          { path: 'recurring', component: Dummy },
          { path: 'categories', component: Dummy },
        ]),
        { provide: AuthService, useValue: auth },
        { provide: ThemeService, useValue: theme },
        { provide: TodoEditorService, useValue: editor },
        { provide: BreakpointObserver, useValue: { observe: () => desktop$ } },
      ],
    });
    const fixture = TestBed.createComponent(Shell);
    await TestBed.inject(Router).navigateByUrl('/two-days');
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  }

  const q = (f: { nativeElement: HTMLElement }, sel: string) => f.nativeElement.querySelector<HTMLElement>(sel);

  it('renders the 4 nav items and marks the active one', async () => {
    const f = await setup();
    const items = Array.from((f.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('nav a'));
    expect(items.map((i) => i.textContent?.trim())).toEqual(['Week', '2 days', 'Recurring', 'Categories']);
    expect(items.map((i) => i.getAttribute('aria-current'))).toEqual([null, 'page', null, null]);
  });

  it('reflects the menu state in aria-expanded', async () => {
    const f = await setup();
    const avatar = q(f, 'button[aria-label="Account menu"]')!;
    expect(avatar.getAttribute('aria-expanded')).toBe('false');
    avatar.click();
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    expect(avatar.getAttribute('aria-expanded')).toBe('true');
  });

  it('computes initials', () => {
    expect(initialsOf('Ada Lovelace')).toBe('AL');
    expect(initialsOf('ada')).toBe('A');
    expect(initialsOf('')).toBe('?');
  });

  it('shows the initials on the avatar button', async () => {
    const f = await setup();
    const avatar = q(f, 'button[aria-label="Account menu"]')!;
    expect(avatar.textContent?.trim()).toBe('AL');
    expect(avatar.getAttribute('aria-haspopup')).toBe('menu');
  });

  it('theme menu item sets the theme and log out logs out', async () => {
    const f = await setup();
    q(f, 'button[aria-label="Account menu"]')!.click();
    f.detectChanges();
    await f.whenStable();
    const items = Array.from(document.querySelectorAll<HTMLElement>('.app-avatar-menu .p-menu-item-link'));
    items.find((i) => i.textContent?.includes('Dark'))!.click();
    expect(theme.set).toHaveBeenCalledWith('DARK');
    items.find((i) => i.textContent?.includes('Log out'))!.click();
    expect(auth.logout).toHaveBeenCalled();
  });

  it('quick toggle flips between light and dark', async () => {
    const f = await setup();
    q(f, 'button[aria-label="Switch to dark theme"]')!.click();
    expect(theme.set).toHaveBeenCalledWith('DARK');
    theme.isDark.set(true);
    f.detectChanges();
    q(f, 'button[aria-label="Switch to light theme"]')!.click();
    expect(theme.set).toHaveBeenCalledWith('LIGHT');
  });

  it('FAB opens the editor with the focused date, else today', async () => {
    const f = await setup();
    TestBed.inject(ShellHeader).focusedDate.set('2026-10-21');
    q(f, 'button[aria-label="New todo"]')!.click();
    expect(editor.openNew).toHaveBeenCalledWith('2026-10-21');
    TestBed.inject(ShellHeader).focusedDate.set(null);
    q(f, 'button[aria-label="New todo"]')!.click();
    expect(editor.openNew).toHaveBeenLastCalledWith(expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));
  });

  it('renders the rail at desktop width and the tab bar below', async () => {
    let f = await setup(true);
    expect(q(f, 'nav.rail')).not.toBeNull();
    expect(q(f, 'nav.tabbar')).toBeNull();
    TestBed.resetTestingModule();
    f = await setup(false);
    expect(q(f, 'nav.tabbar')).not.toBeNull();
    expect(q(f, 'nav.rail')).toBeNull();
  });
});
