import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FieldTree, FormField, email, form, required, submit } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { toApiProblem } from '../../../core/http/api-error';
import { AuthCard } from '../auth-card/auth-card';

@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, RouterLink, ButtonModule, MessageModule, AuthCard],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.scss',
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);

  private readonly model = signal({ email: '' });
  protected readonly forgotForm = form(this.model, (p) => {
    required(p.email, { message: 'Email is required.' });
    email(p.email, { message: 'Enter a valid email address.' });
  });

  protected readonly serverError = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly sentTo = signal<string | null>(null);
  private readonly submitAttempted = signal(false);

  private readonly serverErrorEl = viewChild<ElementRef<HTMLElement>>('serverErrorEl');

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
    await submit(this.forgotForm, {
      action: async () => {
        await this.send();
      },
      onInvalid: () => this.forgotForm.email().focusBoundControl(),
    });
  }

  private async send(): Promise<void> {
    const address = this.model().email.trim();
    this.loading.set(true);
    try {
      await firstValueFrom(this.auth.forgotPassword(address));
      this.sentTo.set(address);
    } catch (err) {
      this.serverError.set(toApiProblem(err).detail);
      setTimeout(() => this.serverErrorEl()?.nativeElement.focus());
    } finally {
      this.loading.set(false);
    }
  }
}
