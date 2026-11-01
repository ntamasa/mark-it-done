import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { Category, itemCountLabel } from '../../core/categories/category.models';
import { CategoryStore } from '../../core/categories/category.store';
import { CategoryChip } from '../../shared/ui/category-chip/category-chip';
import { ShellHeader } from '../shell/shell-header';
import { CategoryDialog } from './category-dialog';

@Component({
  selector: 'app-categories',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CategoryChip, CategoryDialog],
  templateUrl: './categories.html',
  styleUrl: './categories.scss',
})
export class CategoriesPage implements OnInit, OnDestroy {
  private readonly header = inject(ShellHeader);
  protected readonly store = inject(CategoryStore);

  /** Open dialog state: null when closed, category null when creating. */
  protected readonly dialog = signal<{ category: Category | null } | null>(null);
  protected readonly countLabel = itemCountLabel;

  ngOnInit(): void {
    this.header.title.set('Categories');
  }

  ngOnDestroy(): void {
    this.header.title.set('');
    this.header.pager.set(null);
    this.header.focusedDate.set(null);
  }

  protected openNew(): void {
    this.dialog.set({ category: null });
  }

  protected edit(category: Category): void {
    this.dialog.set({ category });
  }
}
