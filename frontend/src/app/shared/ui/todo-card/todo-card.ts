import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { MenuItem } from 'primeng/api';
import { MenuModule } from 'primeng/menu';
import { CategoryStore } from '../../../core/categories/category.store';
import { formatTime } from '../../../core/dates/dates';
import { Todo, isOverdue, isRecurring } from '../../../core/todos/todo.models';
import { CategoryChip } from '../category-chip/category-chip';
import { PriorityTag } from '../priority-tag/priority-tag';
import { RecurringTag } from '../recurring-tag/recurring-tag';

export type TodoCardDensity = 'compact' | 'comfortable';

/** Presentational todo card. Parents handle open, toggleDone and remove (see TodoActions). */
@Component({
  selector: 'app-todo-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MenuModule, PriorityTag, RecurringTag, CategoryChip],
  templateUrl: './todo-card.html',
  styleUrl: './todo-card.scss',
})
export class TodoCard {
  private readonly categories = inject(CategoryStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly todo = input.required<Todo>();
  readonly density = input<TodoCardDensity>('comfortable');

  readonly open = output<void>();
  readonly toggleDone = output<boolean>();
  readonly remove = output<void>();

  protected readonly menuOpen = signal(false);
  protected readonly overdue = computed(() => isOverdue(this.todo()));
  protected readonly recurring = computed(() => isRecurring(this.todo()));
  protected readonly time = computed(() => formatTime(this.todo().dueTime));
  protected readonly category = computed(() => {
    const id = this.todo().categoryId;
    return id ? (this.categories.byId().get(id) ?? null) : null;
  });
  protected readonly firstLine = computed(() => (this.todo().description ?? '').split('\n')[0]);

  protected readonly menuItems: MenuItem[] = [
    { label: 'Edit', icon: 'pi pi-pencil', command: () => this.open.emit() },
    { label: 'Delete', icon: 'pi pi-trash', styleClass: 'menu-destructive', command: () => this.remove.emit() },
  ];

  protected onMenuHide(): void {
    this.menuOpen.set(false);
    this.host.nativeElement.querySelector<HTMLElement>('.more')?.focus();
  }
}
