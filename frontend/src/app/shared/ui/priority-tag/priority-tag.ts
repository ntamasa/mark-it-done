import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { PRIORITY_ICON, PRIORITY_LABEL, PRIORITY_SEVERITY, Priority } from '../../../core/todos/todo.models';

/** Priority as a tag: severity colour plus the label text, so colour is never the only signal. */
@Component({
  selector: 'app-priority-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TagModule],
  template: `<p-tag [severity]="severity()" [icon]="icon()" [value]="label()" />`,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
})
export class PriorityTag {
  readonly priority = input.required<Priority>();

  protected readonly severity = computed(() => PRIORITY_SEVERITY[this.priority()]);
  protected readonly label = computed(() => PRIORITY_LABEL[this.priority()]);
  protected readonly icon = computed(() => `pi ${PRIORITY_ICON[this.priority()]}`);
}
