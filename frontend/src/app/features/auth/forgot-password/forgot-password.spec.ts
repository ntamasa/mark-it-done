import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ForgotPassword } from './forgot-password';

describe('ForgotPassword', () => {
  let fixture: ComponentFixture<ForgotPassword>;
  let el: HTMLElement;
  const forgotPassword = vi.fn();

  beforeEach(async () => {
    forgotPassword.mockReset();
    await TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { forgotPassword, user: () => null, isLoggedIn: () => false } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(ForgotPassword);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  function setEmail(value: string): void {
    const input = el.querySelector<HTMLInputElement>('#forgot-email')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('blocks submit for an empty or invalid email', async () => {
    await submit();
    expect(el.querySelector('.field-error')!.textContent).toContain('Email is required.');
    setEmail('nope');
    await submit();
    expect(el.querySelector('.field-error')!.textContent).toContain('Enter a valid email address.');
    expect(forgotPassword).not.toHaveBeenCalled();
  });

  it('shows the sent state with the typed email on success', async () => {
    forgotPassword.mockReturnValue(of(undefined));
    setEmail('a@b.co');
    await submit();
    expect(forgotPassword).toHaveBeenCalledWith('a@b.co');
    expect(el.querySelector('h1')!.textContent).toContain('Check your email');
    expect(el.querySelector('.status-text')!.textContent).toContain('If an account exists for a@b.co');
  });

  it('shows the problem detail on a network error', async () => {
    forgotPassword.mockReturnValue(throwError(() => ({ status: 0, error: null })));
    setEmail('a@b.co');
    await submit();
    expect(el.querySelector('p-message')).not.toBeNull();
    expect(el.querySelector('h1')!.textContent).toContain('Forgot password');
  });
});
