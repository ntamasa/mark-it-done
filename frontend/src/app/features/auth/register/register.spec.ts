import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { throwError, of } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { User } from '../../../core/auth/auth.models';
import { Register } from './register';

const USER: User = { id: '1', email: 'a@b.co', displayName: 'Ann', timezone: 'UTC', themePreference: 'LIGHT' };

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let el: HTMLElement;
  const register = vi.fn();
  let navigate: ReturnType<typeof vi.spyOn>;
  let messages: MessageService;

  beforeEach(async () => {
    register.mockReset();
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService,
        { provide: AuthService, useValue: { register, user: () => null, isLoggedIn: () => false } },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    messages = TestBed.inject(MessageService);
    fixture = TestBed.createComponent(Register);
    el = fixture.nativeElement;
    fixture.detectChanges();
  });

  function setValue(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>('#' + id)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function fillValid(): void {
    setValue('reg-name', 'Ann');
    setValue('reg-email', 'a@b.co');
    setValue('reg-password', 'Abcdefghij1!');
    setValue('reg-confirm', 'Abcdefghij1!');
  }

  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const alerts = () => Array.from(el.querySelectorAll('.field-error')).map((e) => e.textContent!.trim());

  it('shows the three required messages on an empty submit and does not call the API', async () => {
    await submit();
    expect(alerts()).toEqual([
      'Display name is required.',
      'Email is required.',
      'Password is required.',
      'Please repeat the password.',
    ]);
    expect(register).not.toHaveBeenCalled();
  });

  it('shows the weak password message with 1 of 5 rules met for "abc"', async () => {
    setValue('reg-password', 'abc');
    await submit();
    expect(alerts()).toContain("Password doesn't meet all requirements.");
    expect(el.querySelectorAll('.rule.met').length).toBe(1);
  });

  it('shows a mismatch message', async () => {
    setValue('reg-password', 'Abcdefghij1!');
    setValue('reg-confirm', 'Different1!xyz');
    await submit();
    expect(alerts()).toContain("Passwords don't match.");
  });

  it('has no invalid styling before touch, but has it after blur on an invalid field', () => {
    const inputs = Array.from(el.querySelectorAll<HTMLInputElement>('input'));
    for (const i of inputs) {
      expect(i.classList.contains('p-invalid')).toBe(false);
      expect(i.getAttribute('aria-invalid')).toBeNull();
    }
    const name = el.querySelector<HTMLInputElement>('#reg-name')!;
    name.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(name.getAttribute('aria-invalid')).toBe('true');
  });

  it('clears the server error when the email is edited', async () => {
    register.mockReturnValue(
      throwError(() => ({ status: 409, error: { code: 'EMAIL_TAKEN', detail: 'x', errors: [] } })),
    );
    fillValid();
    await submit();
    expect(el.querySelector('p-message')).not.toBeNull();
    setValue('reg-email', 'other@b.co');
    expect(el.querySelector('p-message')).toBeNull();
  });

  it('shows an invalid email message', async () => {
    setValue('reg-email', 'nope');
    await submit();
    expect(alerts()).toContain('Enter a valid email address.');
  });

  it('registers with the browser time zone and navigates to /week with a welcome toast', async () => {
    register.mockReturnValue(of(USER));
    const add = vi.spyOn(messages, 'add');
    fillValid();
    await submit();
    expect(register).toHaveBeenCalledWith({
      displayName: 'Ann',
      email: 'a@b.co',
      password: 'Abcdefghij1!',
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    });
    expect(navigate).toHaveBeenCalledWith(['/week']);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Welcome, Ann!' }));
  });

  it('shows the conflict message for EMAIL_TAKEN', async () => {
    register.mockReturnValue(
      throwError(() => ({ status: 409, error: { code: 'EMAIL_TAKEN', detail: 'x', errors: [] } })),
    );
    fillValid();
    await submit();
    expect(el.querySelector('p-message')!.textContent).toContain('An account with this email already exists.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('toggles password visibility', () => {
    const btn = el.querySelector<HTMLButtonElement>('.eye-btn')!;
    expect(btn.getAttribute('aria-label')).toBe('Show password');
    btn.click();
    fixture.detectChanges();
    expect(btn.getAttribute('aria-label')).toBe('Hide password');
    expect(el.querySelector<HTMLInputElement>('#reg-password')!.type).toBe('text');
  });
});
