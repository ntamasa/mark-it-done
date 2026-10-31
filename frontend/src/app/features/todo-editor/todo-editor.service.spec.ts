import { TestBed } from '@angular/core/testing';
import { Todo } from '../../core/todos/todo.models';
import { TodoEditorService } from './todo-editor.service';

const todo: Todo = {
  id: '1',
  name: 't',
  description: null,
  dueDate: '2026-10-21',
  dueTime: null,
  priority: 'LOW',
  categoryId: null,
  done: false,
  doneAt: null,
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  expired: false,
  seriesId: null,
  occurrenceDate: null,
  ruleSummary: null,
};

describe('TodoEditorService', () => {
  let svc: TodoEditorService;
  beforeEach(() => (svc = TestBed.inject(TodoEditorService)));

  it('opens a new todo with the default date', () => {
    svc.openNew('2026-10-21', { repeat: true });
    expect(svc.state()).toEqual({ mode: 'new', todo: null, defaultDate: '2026-10-21', repeat: true });
  });

  it('opens an edit with the scope and closes', () => {
    svc.openEdit(todo, { defaultScope: 'FUTURE' });
    expect(svc.state()?.mode).toBe('edit');
    expect(svc.state()?.defaultScope).toBe('FUTURE');
    svc.close();
    expect(svc.state()).toBeNull();
  });

  it('restores focus to the opener on close', () => {
    const btn = document.createElement('button');
    document.body.appendChild(btn);
    btn.focus();
    svc.openNew();
    document.body.focus();
    svc.close();
    expect(document.activeElement).toBe(btn);
    btn.remove();
  });
});
