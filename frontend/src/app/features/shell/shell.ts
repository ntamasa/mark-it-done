import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { MenuModule } from 'primeng/menu';
import { map } from 'rxjs';
import { ThemePreference } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { todayIso } from '../../core/dates/dates';
import { ThemeService } from '../../core/theme/theme.service';
import { PagerComponent } from '../../shared/ui/pager/pager';
import { TodoEditorService } from '../todo-editor/todo-editor.service';
import { ShellHeader } from './shell-header';

export const DESKTOP_QUERY = '(min-width: 1024px)';

interface NavItem {
  label: string;
  icon: string;
  path: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Week', icon: 'pi pi-calendar', path: '/week' },
  { label: '2 days', icon: 'pi pi-clone', path: '/two-days' },
  { label: 'Recurring', icon: 'pi pi-sync', path: '/recurring' },
  { label: 'Categories', icon: 'pi pi-tags', path: '/categories' },
];

const THEME_OPTIONS: { label: string; value: ThemePreference }[] = [
  { label: 'System', value: 'SYSTEM' },
  { label: 'Light', value: 'LIGHT' },
  { label: 'Dark', value: 'DARK' },
];

/** Initials from a display name: first letters of the first and last words. */
export function initialsOf(name: string | null | undefined): string {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '?';
  }
  const first = words[0][0];
  const last = words.length > 1 ? words[words.length - 1][0] : '';
  return (first + last).toUpperCase();
}

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MenuModule, PagerComponent],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly editor = inject(TodoEditorService);
  protected readonly header = inject(ShellHeader);

  protected readonly menuOpen = signal(false);
  protected readonly navItems = NAV_ITEMS;
  protected readonly isDesktop = toSignal(
    inject(BreakpointObserver)
      .observe(DESKTOP_QUERY)
      .pipe(map((s) => s.matches)),
    { initialValue: false },
  );

  protected readonly initials = computed(() => initialsOf(this.auth.user()?.displayName));
  protected readonly isDark = this.theme.isDark;

  protected readonly menuItems = computed<MenuItem[]>(() => {
    const current = this.theme.preference();
    return [
      {
        label: 'Theme',
        items: THEME_OPTIONS.map((o) => ({
          label: o.label,
          icon: current === o.value ? 'pi pi-check' : 'pi pi-fw',
          automationId: `theme-${o.value}`,
          command: () => this.theme.set(o.value),
        })),
      },
      { separator: true },
      { label: 'Categories', icon: 'pi pi-tags', routerLink: '/categories' },
      { label: 'Log out', icon: 'pi pi-sign-out', styleClass: 'menu-destructive', command: () => this.auth.logout() },
    ];
  });

  protected toggleTheme(): void {
    this.theme.set(this.theme.isDark() ? 'LIGHT' : 'DARK');
  }

  protected newTodo(): void {
    this.editor.openNew(this.header.focusedDate() ?? todayIso());
  }
}
