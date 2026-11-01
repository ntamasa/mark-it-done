import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Category, SWATCHES } from '../../../core/categories/category.models';

export type ChipSize = 'sm' | 'md';

@Component({
  selector: 'app-category-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (category(); as c) {
      <span
        class="chip"
        [class.chip-sm]="size() === 'sm'"
        [style.background-color]="colours().background"
        [style.color]="colours().text"
        [attr.title]="c.name"
        >{{ c.name }}</span
      >
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      max-width: 100%;
    }
    .chip {
      display: block;
      max-width: 100%;
      box-sizing: border-box;
      height: 26px;
      line-height: 26px;
      padding: 0 12px;
      border-radius: var(--radius-pill);
      font-size: 13px;
      font-weight: 700;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .chip-sm {
      height: 22px;
      line-height: 22px;
      padding: 0 10px;
      font-size: 12px;
    }
  `,
})
export class CategoryChip {
  readonly category = input<Category | null>(null);
  readonly size = input<ChipSize>('md');

  protected readonly colours = computed(() => SWATCHES[this.category()?.swatch ?? 'TEAL']);
}
