import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { ThemePreference } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';

export const THEME_STORAGE_KEY = 'markitdone.theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';
const NEXT: Record<ThemePreference, ThemePreference> = { SYSTEM: 'LIGHT', LIGHT: 'DARK', DARK: 'SYSTEM' };

function isPreference(v: unknown): v is ThemePreference {
  return v === 'SYSTEM' || v === 'LIGHT' || v === 'DARK';
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly auth = inject(AuthService);
  private readonly doc = inject(DOCUMENT);

  private readonly media: MediaQueryList | null = this.doc.defaultView?.matchMedia?.(DARK_QUERY) ?? null;
  private readonly prefSignal = signal<ThemePreference>(this.readCache());
  private readonly osDark = signal(this.media?.matches ?? false);
  private chosenThisSession = false;

  readonly preference = this.prefSignal.asReadonly();
  readonly isDark = computed(() => this.prefSignal() === 'DARK' || (this.prefSignal() === 'SYSTEM' && this.osDark()));

  constructor() {
    this.media?.addEventListener('change', (e) => this.osDark.set(e.matches));

    effect(() => {
      this.doc.documentElement.classList.toggle('app-dark', this.isDark());
    });

    // The server preference wins over the local cache, unless the user chose in this session;
    // in that case the session choice is pushed to the server instead.
    effect(() => {
      const server = this.auth.user()?.themePreference;
      if (!server || !isPreference(server)) {
        return;
      }
      untracked(() => {
        if (!this.chosenThisSession) {
          this.prefSignal.set(server);
          this.writeCache(server);
        } else if (this.prefSignal() !== server) {
          this.auth.updateMe({ themePreference: this.prefSignal() }).subscribe({ error: () => undefined });
        }
      });
    });
  }

  set(p: ThemePreference): void {
    this.chosenThisSession = true;
    this.prefSignal.set(p);
    this.writeCache(p);
    if (this.auth.isLoggedIn()) {
      this.auth.updateMe({ themePreference: p }).subscribe({ error: () => undefined });
    }
  }

  cycle(): void {
    this.set(NEXT[this.prefSignal()]);
  }

  private readCache(): ThemePreference {
    try {
      const v = this.doc.defaultView?.localStorage.getItem(THEME_STORAGE_KEY);
      return isPreference(v) ? v : 'SYSTEM';
    } catch {
      return 'SYSTEM';
    }
  }

  private writeCache(p: ThemePreference): void {
    try {
      this.doc.defaultView?.localStorage.setItem(THEME_STORAGE_KEY, p);
    } catch {
      // ignore
    }
  }
}
