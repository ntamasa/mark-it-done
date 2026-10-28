import { TestBed } from '@angular/core/testing';
import { PasswordRules, evaluatePasswordRules, isStrongPassword } from './password-rules';

describe('isStrongPassword', () => {
  it('accepts a password meeting all rules', () => {
    expect(isStrongPassword('Abcdefghij1!')).toBe(true);
  });

  it.each([
    ['too short', 'Abcdefgh1!'],
    ['no uppercase', 'abcdefghij1!'],
    ['no lowercase', 'ABCDEFGHIJ1!'],
    ['no digit', 'Abcdefghijk!'],
    ['no symbol', 'Abcdefghijk1'],
    ['over 128 characters', 'Aa1!' + 'a'.repeat(125)],
  ])('rejects %s', (_name, value) => {
    expect(isStrongPassword(value)).toBe(false);
  });

  it('accepts exactly 128 characters', () => {
    expect(isStrongPassword('Aa1!' + 'a'.repeat(124))).toBe(true);
  });
});

describe('PasswordRules', () => {
  function render(password: string) {
    const fixture = TestBed.createComponent(PasswordRules);
    fixture.componentRef.setInput('password', password);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function met(el: HTMLElement, key: string): boolean {
    return el.querySelector(`[data-rule="${key}"]`)!.classList.contains('met');
  }

  it.each([
    ['length', 'aaaaaaaaaaaa'],
    ['upper', 'A'],
    ['lower', 'a'],
    ['digit', '1'],
    ['symbol', '!'],
  ])('meets only the %s rule for "%s"', (key, value) => {
    const el = render(value);
    for (const r of evaluatePasswordRules('')) {
      const expected = r.key === key || (key === 'length' && r.key === 'lower');
      expect(met(el, r.key)).toBe(expected);
    }
  });

  it('shows 1 of 5 rules met for "abc" with sr-only text', () => {
    const el = render('abc');
    expect(el.querySelectorAll('.rule.met').length).toBe(1);
    expect(el.querySelectorAll('.pi-check').length).toBe(1);
    expect(el.querySelectorAll('.pi-circle').length).toBe(4);
    expect(el.textContent).toContain('(met)');
    expect(el.textContent).toContain('(not met)');
  });
});
