import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FieldTree, FormField, form, required, submit, validate } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/http/api-error';
import { NotifyService } from '../../../core/ui/notify.service';
import { PasswordRules, isStrongPassword } from '../../../shared/ui/password-rules/password-rules';
import { AuthCard } from '../auth-card/auth-card';

@Component({
  selector: 'app-reset-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, RouterLink, ButtonModule, MessageModule, AuthCard, PasswordRules],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.scss',
})
export class ResetPassword {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '';

  protected readonly expired = signal(this.token === '');

  private readonly model = signal({ password: '', confirmPassword: '' });
  protected readonly resetForm = form(this.model, (p) => {
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
    await submit(this.resetForm, {
      action: async () => {
        await this.reset();
      },
      onInvalid: () => this.focusFirstInvalid(),
    });
  }

  private focusFirstInvalid(): void {
    [this.resetForm.password, this.resetForm.confirmPassword]
      .find((f) => f().invalid())?.()
      .focusBoundControl();
  }

  private async reset(): Promise<void> {
    this.loading.set(true);
    try {
      await firstValueFrom(this.auth.resetPassword(this.token, this.model().password));
      await this.router.navigate(['/login']);
      this.notify.success('Password updated. Please log in.');
    } catch (err) {
      const problem = toApiProblem(err);
      if (problem.code === 'RESET_TOKEN_INVALID') {
        this.expired.set(true);
      } else {
        this.serverError.set(problem.detail);
        setTimeout(() => this.serverErrorEl()?.nativeElement.focus());
      }
    } finally {
      this.loading.set(false);
    }
  }
}
