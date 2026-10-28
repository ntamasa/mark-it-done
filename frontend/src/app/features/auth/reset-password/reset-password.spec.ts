import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ResetPassword } from './reset-password';

describe('ResetPassword', () => {
  const resetPassword = vi.fn();
  let el: HTMLElement;
  let fixture: ComponentFixture<ResetPassword>;
  let navigate: ReturnType<typeof vi.spyOn>;
  let messages: MessageService;

  async function setup(token: string | null): Promise<void> {
    resetPassword.mockReset();
    await TestBed.configureTestingModule({
      imports: [ResetPassword],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        MessageService,
        { provide: AuthService, useValue: { resetPassword } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
        },
      ],
    }).compileComponents();
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    messages = TestBed.inject(MessageService);
    fixture = TestBed.createComponent(ResetPassword);
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  function setValue(id: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>('#' + id)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const fillValid = () => {
    setValue('reset-password', 'Abcdefghij1!');
    setValue('reset-confirm', 'Abcdefghij1!');
  };

  it('shows Link expired when the token is missing', async () => {
    await setup(null);
    expect(el.querySelector('h1')!.textContent).toContain('Link expired');
    expect(el.querySelector('form')).toBeNull();
    expect(el.querySelector('a[href="/forgot-password"]')!.textContent).toContain('Request a new link');
  });

  it('shows a mismatch error and does not call the API', async () => {
    await setup('tok');
    setValue('reset-password', 'Abcdefghij1!');
    setValue('reset-confirm', 'Different1!xyz');
    await submit();
    expect(el.querySelector('.field-error')!.textContent).toContain("Passwords don't match.");
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it('shows the expired screen on RESET_TOKEN_INVALID', async () => {
    await setup('tok');
    resetPassword.mockReturnValue(
      throwError(() => ({ status: 400, error: { code: 'RESET_TOKEN_INVALID', detail: 'x', errors: [] } })),
    );
    fillValid();
    await submit();
    expect(el.querySelector('h1')!.textContent).toContain('Link expired');
  });

  it('shows the server detail for other errors', async () => {
    await setup('tok');
    resetPassword.mockReturnValue(
      throwError(() => ({ status: 400, error: { code: 'VALIDATION_FAILED', detail: 'Bad password', errors: [] } })),
    );
    fillValid();
    await submit();
    expect(el.querySelector('p-message')!.textContent).toContain('Bad password');
    expect(el.querySelector('form')).not.toBeNull();
  });

  it('goes to /login with a toast on success', async () => {
    await setup('tok');
    resetPassword.mockReturnValue(of(undefined));
    const add = vi.spyOn(messages, 'add');
    fillValid();
    await submit();
    expect(resetPassword).toHaveBeenCalledWith('tok', 'Abcdefghij1!');
    expect(navigate).toHaveBeenCalledWith(['/login']);
    expect(add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Password updated. Please log in.' }));
  });
});
