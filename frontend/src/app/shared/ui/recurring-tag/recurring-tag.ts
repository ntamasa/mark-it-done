import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

/** "Recurring" tag; the tooltip carries the rule summary. */
@Component({
  selector: 'app-recurring-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TagModule, TooltipModule],
  template: `<p-tag severity="contrast" icon="pi pi-sync" value="Recurring" [pTooltip]="summary() ?? ''" />`,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
})
export class RecurringTag {
  readonly summary = input<string | null>(null);
}
