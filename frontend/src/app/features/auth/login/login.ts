import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FieldTree, FormField, email, form, required, submit } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/http/api-error';
import { AuthCard } from '../auth-card/auth-card';

const INVALID_CREDENTIALS_MESSAGE = 'Email or password is incorrect.';
const DEFAULT_URL = '/week';

interface LoginModel {
  email: string;
  password: string;
  rememberMe: boolean;
}

/** Accepts only same-origin absolute paths: starts with "/" but not "//" (or "/\"). */
export function safeReturnUrl(url: string | null | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\') ? url : DEFAULT_URL;
}

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, RouterLink, ButtonModule, MessageModule, AuthCard],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly model = signal<LoginModel>({ email: '', password: '', rememberMe: true });

  protected readonly loginForm = form(this.model, (p) => {
    required(p.email, { message: 'Email is required.' });
    email(p.email, { message: 'Enter a valid email address.' });
    required(p.password, { message: 'Password is required.' });
  });

  protected readonly showPassword = signal(false);
  protected readonly serverError = signal<string | null>(null);
  protected readonly loading = signal(false);
  private readonly submitAttempted = signal(false);

  private readonly serverErrorEl = viewChild<ElementRef<HTMLElement>>('serverErrorEl');

  protected togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  /** The first error message of a field, shown after blur or a submit attempt. */
  protected errorOf(field: FieldTree<string>): string | null {
    const state = field();
    if (!(state.touched() || this.submitAttempted())) {
      return null;
    }
    return state.errors()[0]?.message ?? null;
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.loading()) {
      return;
    }
    this.submitAttempted.set(true);
    this.serverError.set(null);
    await submit(this.loginForm, {
      action: async () => {
        await this.login();
      },
      onInvalid: () => this.focusFirstInvalid(),
    });
  }

  private focusFirstInvalid(): void {
    [this.loginForm.email, this.loginForm.password]
      .find((f) => f().invalid())?.()
      .focusBoundControl();
  }

  private async login(): Promise<void> {
    const value = this.model();
    this.loading.set(true);
    try {
      await firstValueFrom(
        this.auth.login({
          email: value.email.trim(),
          password: value.password,
          rememberMe: value.rememberMe,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        }),
      );
      await this.router.navigateByUrl(safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')));
    } catch (err) {
      const problem = toApiProblem(err);
      this.serverError.set(
        problem.code === 'INVALID_CREDENTIALS' || problem.status === 401 ? INVALID_CREDENTIALS_MESSAGE : problem.detail,
      );
      setTimeout(() => this.serverErrorEl()?.nativeElement.focus());
    } finally {
      this.loading.set(false);
    }
  }
}
