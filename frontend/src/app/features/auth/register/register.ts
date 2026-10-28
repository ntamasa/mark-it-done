import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FieldTree, FormField, email, form, required, submit, validate } from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/http/api-error';
import { NotifyService } from '../../../core/ui/notify.service';
import { PasswordRules, isStrongPassword } from '../../../shared/ui/password-rules/password-rules';
import { AuthCard } from '../auth-card/auth-card';

const EMAIL_TAKEN_MESSAGE = 'An account with this email already exists.';

interface RegisterModel {
  displayName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, RouterLink, ButtonModule, MessageModule, AuthCard, PasswordRules],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);

  private readonly model = signal<RegisterModel>({
    displayName: '',
    email: '',
    password: '',
    confirmPassword: '',
  });

  protected readonly registerForm = form(this.model, (p) => {
    required(p.displayName, { message: 'Display name is required.' });
    required(p.email, { message: 'Email is required.' });
    email(p.email, { message: 'Enter a valid email address.' });
    required(p.password, { message: 'Password is required.' });
    validate(p.password, ({ value }) =>
      value() && !isStrongPassword(value())
        ? { kind: 'weakPassword', message: "Password doesn't meet all requirements." }
        : undefined,
    );
    required(p.confirmPassword, { message: 'Please repeat the password.' });
    validate(p.confirmPassword, ({ value, valueOf }) =>
      value() && value() !== valueOf(p.password) ? { kind: 'mismatch', message: "Passwords don't match." } : undefined,
    );
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
    await submit(this.registerForm, {
      action: async () => {
        await this.register();
      },
      onInvalid: () => this.focusFirstInvalid(),
    });
  }

  private focusFirstInvalid(): void {
    const fields = [
      this.registerForm.displayName,
      this.registerForm.email,
      this.registerForm.password,
      this.registerForm.confirmPassword,
    ];
    fields
      .find((f) => f().invalid())?.()
      .focusBoundControl();
  }

  private async register(): Promise<void> {
    const value = this.model();
    this.loading.set(true);
    try {
      const user = await firstValueFrom(
        this.auth.register({
          displayName: value.displayName.trim(),
          email: value.email.trim(),
          password: value.password,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        }),
      );
      await this.router.navigate(['/week']);
      this.notify.success(`Welcome, ${user.displayName}!`);
    } catch (err) {
      const problem = toApiProblem(err);
      this.serverError.set(
        problem.code === 'EMAIL_TAKEN' || problem.status === 409 ? EMAIL_TAKEN_MESSAGE : problem.detail,
      );
      setTimeout(() => this.serverErrorEl()?.nativeElement.focus());
    } finally {
      this.loading.set(false);
    }
  }
}
