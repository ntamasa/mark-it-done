import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { Pager } from '../../../features/shell/shell-header';

@Component({
  selector: 'app-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule],
  template: `
    @let p = pager();
    <div class="pager">
      <p-button
        icon="pi pi-chevron-left"
        [text]="true"
        [rounded]="true"
        severity="secondary"
        [ariaLabel]="p.prevLabel"
        (onClick)="p.prev()"
      />
      <h1 class="pager-label" aria-live="polite">{{ p.label }}</h1>
      <p-button
        icon="pi pi-chevron-right"
        [text]="true"
        [rounded]="true"
        severity="secondary"
        [ariaLabel]="p.nextLabel"
        (onClick)="p.next()"
      />
      <p-button
        label="Today"
        size="small"
        severity="secondary"
        [outlined]="true"
        [disabled]="p.todayDisabled"
        (onClick)="p.today()"
      />
    </div>
  `,
  styles: `
    .pager {
      display: flex;
      align-items: center;
      gap: var(--space-xs);
    }
    .pager-label {
      margin: 0;
      font-size: 28px;
      line-height: 36px;
      font-weight: 700;
      color: var(--color-foreground);
      white-space: nowrap;
    }
  `,
})
export class PagerComponent {
  readonly pager = input.required<Pager>();
}
