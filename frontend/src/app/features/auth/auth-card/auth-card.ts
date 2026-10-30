import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ThemePreference } from '../../../core/auth/auth.models';
import { ThemeService } from '../../../core/theme/theme.service';

const ICONS: Record<ThemePreference, string> = {
  SYSTEM: 'pi pi-desktop',
  LIGHT: 'pi pi-sun',
  DARK: 'pi pi-moon',
};
const LABELS: Record<ThemePreference, string> = { SYSTEM: 'System', LIGHT: 'Light', DARK: 'Dark' };

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

  private readonly theme = inject(ThemeService);
  protected readonly themeIcon = computed(() => ICONS[this.theme.preference()]);
  protected readonly themeLabel = computed(() => 'Theme: ' + LABELS[this.theme.preference()]);

  protected cycleTheme(): void {
    this.theme.cycle();
  }
}
