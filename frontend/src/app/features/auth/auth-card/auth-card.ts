import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

type ThemeMode = 'system' | 'light' | 'dark';

const NEXT_MODE: Record<ThemeMode, ThemeMode> = { system: 'light', light: 'dark', dark: 'system' };
const ICONS: Record<ThemeMode, string> = {
  system: 'pi pi-desktop',
  light: 'pi pi-sun',
  dark: 'pi pi-moon',
};

/** Shared glass card layout for the auth pages. */
@Component({
  selector: 'app-auth-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './auth-card.html',
  styleUrl: './auth-card.scss',
})
export class AuthCard {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();

  // Until ThemeService (MAR-64) exists, toggle .app-dark directly.
  protected readonly mode = signal<ThemeMode>('system');
  protected readonly themeIcon = computed(() => ICONS[this.mode()]);

  protected cycleTheme(): void {
    const next = NEXT_MODE[this.mode()];
    this.mode.set(next);
    const dark = next === 'dark' || (next === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    document.documentElement.classList.toggle('app-dark', dark);
  }
}
