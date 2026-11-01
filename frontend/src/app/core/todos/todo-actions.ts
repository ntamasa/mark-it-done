import { Injectable, inject } from '@angular/core';
import { formatDay } from '../dates/dates';
import { toApiProblem } from '../http/api-error';
import { ConfirmService } from '../ui/confirm.service';
import { NotifyService } from '../ui/notify.service';
import { TodoApi } from './todo.api';
import { Todo, isRecurring } from './todo.models';

/** Shared done and delete actions used by the views that show todo cards. */
@Injectable({ providedIn: 'root' })
export class TodoActions {
  private readonly api = inject(TodoApi);
  private readonly confirm = inject(ConfirmService);
  private readonly notify = inject(NotifyService);

  /**
   * Optimistic done toggle. apply is called at once with the flipped todo, then with the server's todo on success
   * or with the original todo (revert) on failure, which also shows the error toast. Resolves to true on success.
   */
  async toggleDone(todo: Todo, apply: (todo: Todo) => void): Promise<boolean> {
    apply({ ...todo, done: !todo.done });
    try {
      const saved = await new Promise<Todo>((resolve, reject) => {
        this.api.setDone(todo.id, !todo.done).subscribe({ next: resolve, error: reject });
      });
      apply(saved);
      return true;
    } catch (err) {
      apply(todo);
      this.notify.error(toApiProblem(err));
      return false;
    }
  }

  /** Asks for confirmation, deletes, then toasts. Resolves to true when the todo was deleted. */
  async confirmDelete(todo: Todo): Promise<boolean> {
    const occurrence = isRecurring(todo);
    const accepted = await this.confirm.confirmDanger(
      occurrence
        ? {
            header: 'Delete this occurrence?',
            message: `Only ${formatDay(todo.occurrenceDate ?? todo.dueDate)} is removed. “${todo.name}” keeps repeating.`,
            acceptLabel: 'Delete occurrence',
          }
        : {
            header: `Delete “${todo.name}”?`,
            message: 'This todo will be permanently removed.',
            acceptLabel: 'Delete',
          },
    );
    if (!accepted) {
      return false;
    }
    try {
      await new Promise<void>((resolve, reject) => {
        this.api.delete(todo.id).subscribe({ next: () => resolve(), error: reject });
      });
    } catch (err) {
      this.notify.error(toApiProblem(err));
      return false;
    }
    this.notify.success(occurrence ? 'Occurrence deleted' : 'Todo deleted');
    return true;
  }
}
