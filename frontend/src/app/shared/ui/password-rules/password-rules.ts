import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface PasswordRuleResult {
  key: 'length' | 'upper' | 'lower' | 'digit' | 'symbol';
  label: string;
  met: boolean;
}

const MIN_LENGTH = 12;
const MAX_LENGTH = 128;

/** Evaluates the five password rules (mirrors the backend StrongPasswordValidator, MAR-50). */
export function evaluatePasswordRules(p: string): PasswordRuleResult[] {
  return [
    { key: 'length', label: '12+ characters', met: p.length >= MIN_LENGTH },
    { key: 'upper', label: 'Uppercase', met: /[A-Z]/.test(p) },
    { key: 'lower', label: 'Lowercase', met: /[a-z]/.test(p) },
    { key: 'digit', label: 'Digit', met: /[0-9]/.test(p) },
    { key: 'symbol', label: 'Symbol', met: /[^A-Za-z0-9]/.test(p) },
  ];
}

/** Exactly the backend policy: 12-128 characters plus uppercase, lowercase, digit and symbol. */
export function isStrongPassword(p: string): boolean {
  return p.length <= MAX_LENGTH && evaluatePasswordRules(p).every((r) => r.met);
}

@Component({
  selector: 'app-password-rules',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="rules" [id]="listId()" aria-label="Password requirements">
      @for (rule of rules(); track rule.key) {
        <li class="rule" [class.met]="rule.met" [attr.data-rule]="rule.key">
          <i [class]="rule.met ? 'pi pi-check' : 'pi pi-circle'" aria-hidden="true"></i>
          <span
            >{{ rule.label }}<span class="sr-only">{{ rule.met ? ' (met)' : ' (not met)' }}</span></span
          >
        </li>
      }
    </ul>
  `,
  styles: `
    .rules {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-xs) var(--space-md);
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .rule {
      display: flex;
      align-items: center;
      gap: var(--space-sm);
      font-size: 14px;
      line-height: 20px;
      color: var(--color-muted-foreground);
    }
    .rule.met {
      color: var(--color-primary-strong);
    }
    .rule i {
      font-size: 12px;
    }
  `,
})
export class PasswordRules {
  readonly password = input('');
  readonly listId = input('password-rules');

  protected readonly rules = computed(() => evaluatePasswordRules(this.password()));
}
