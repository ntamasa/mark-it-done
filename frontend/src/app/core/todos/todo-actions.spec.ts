import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ConfirmService } from '../ui/confirm.service';
import { NotifyService } from '../ui/notify.service';
import { TodoApi } from './todo.api';
import { Todo } from './todo.models';
import { TodoActions } from './todo-actions';

const todo: Todo = {
  id: '1',
  name: 'Buy groceries',
  description: null,
  dueDate: '2026-10-21',
  dueTime: null,
  priority: 'LOW',
  categoryId: null,
  done: false,
  doneAt: null,
  createdAt: '',
  updatedAt: '',
  expired: false,
  seriesId: null,
  occurrenceDate: null,
  ruleSummary: null,
};

describe('TodoActions', () => {
  const api = { setDone: vi.fn(), delete: vi.fn() };
  const confirm = { confirmDanger: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  let actions: TodoActions;

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      providers: [
        { provide: TodoApi, useValue: api },
        { provide: ConfirmService, useValue: confirm },
        { provide: NotifyService, useValue: notify },
      ],
    });
    actions = TestBed.inject(TodoActions);
  });

  it('flips at once and applies the saved todo', async () => {
    const saved = { ...todo, done: true, doneAt: 'x' };
    api.setDone.mockReturnValue(of(saved));
    const seen: Todo[] = [];
    expect(await actions.toggleDone(todo, (t) => seen.push(t))).toBe(true);
    expect(api.setDone).toHaveBeenCalledWith('1', true);
    expect(seen).toEqual([{ ...todo, done: true }, saved]);
  });

  it('reverts and shows the error toast on failure', async () => {
    api.setDone.mockReturnValue(throwError(() => ({ status: 0, error: null })));
    const seen: Todo[] = [];
    expect(await actions.toggleDone(todo, (t) => seen.push(t))).toBe(false);
    expect(seen).toEqual([{ ...todo, done: true }, todo]);
    expect(notify.error).toHaveBeenCalled();
  });

  it('confirms a one-off delete with its texts', async () => {
    confirm.confirmDanger.mockResolvedValue(true);
    api.delete.mockReturnValue(of(undefined));
    expect(await actions.confirmDelete(todo)).toBe(true);
    expect(confirm.confirmDanger).toHaveBeenCalledWith({
      header: 'Delete “Buy groceries”?',
      message: 'This todo will be permanently removed.',
      acceptLabel: 'Delete',
    });
    expect(api.delete).toHaveBeenCalledWith('1');
    expect(notify.success).toHaveBeenCalledWith('Todo deleted');
  });

  it('confirms an occurrence delete with its texts', async () => {
    confirm.confirmDanger.mockResolvedValue(true);
    api.delete.mockReturnValue(of(undefined));
    await actions.confirmDelete({ ...todo, seriesId: 's', occurrenceDate: '2026-10-21' });
    expect(confirm.confirmDanger).toHaveBeenCalledWith({
      header: 'Delete this occurrence?',
      message: 'Only Wed, Oct 21 is removed. “Buy groceries” keeps repeating.',
      acceptLabel: 'Delete occurrence',
    });
    expect(notify.success).toHaveBeenCalledWith('Occurrence deleted');
  });

  it('does nothing when the confirmation is rejected', async () => {
    confirm.confirmDanger.mockResolvedValue(false);
    expect(await actions.confirmDelete(todo)).toBe(false);
    expect(api.delete).not.toHaveBeenCalled();
  });

  it('shows the error toast when the delete fails', async () => {
    confirm.confirmDanger.mockResolvedValue(true);
    api.delete.mockReturnValue(throwError(() => ({ status: 0, error: null })));
    expect(await actions.confirmDelete(todo)).toBe(false);
    expect(notify.error).toHaveBeenCalled();
    expect(notify.success).not.toHaveBeenCalled();
  });
});
