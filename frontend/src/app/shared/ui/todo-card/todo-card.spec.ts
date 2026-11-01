import { TestBed } from '@angular/core/testing';
import { Todo } from '../../../core/todos/todo.models';
import { CategoryStore } from '../../../core/categories/category.store';
import { TodoCard } from './todo-card';

const base: Todo = {
  id: '1',
  name: 'Buy groceries',
  description: 'Milk\nEggs',
  dueDate: '2026-10-21',
  dueTime: '14:30:00',
  priority: 'HIGH',
  categoryId: 'c1',
  done: false,
  doneAt: null,
  createdAt: '',
  updatedAt: '',
  expired: false,
  seriesId: null,
  occurrenceDate: null,
  ruleSummary: null,
};

describe('TodoCard', () => {
  function render(patch: Partial<Todo> = {}, density?: 'compact' | 'comfortable') {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CategoryStore,
          useValue: { byId: () => new Map([['c1', { id: 'c1', name: 'Work', swatch: 'TEAL', itemCount: 0 }]]) },
        },
      ],
    });
    const fixture = TestBed.createComponent(TodoCard);
    fixture.componentRef.setInput('todo', { ...base, ...patch });
    if (density) {
      fixture.componentRef.setInput('density', density);
    }
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, card: el.querySelector<HTMLElement>('.card')!, text: el.textContent ?? '' };
  }

  it('shows time, priority and category', () => {
    const { text, el } = render();
    expect(text).toContain('14:30');
    expect(text).toContain('High');
    expect(text).toContain('Work');
    expect(el.querySelector('.desc')?.textContent).toBe('Milk');
  });

  it('shows the overdue style and text, and neither when done', () => {
    const overdue = render({ expired: true });
    expect(overdue.card.classList.contains('overdue')).toBe(true);
    expect(overdue.text).toContain('14:30 · Overdue');
    expect(overdue.el.querySelector('.sr-only')?.textContent).toBe('Overdue');

    TestBed.resetTestingModule();
    const done = render({ expired: true, done: true });
    expect(done.card.classList.contains('overdue')).toBe(false);
    expect(done.text).not.toContain('Overdue');
  });

  it('shows All day without a time', () => {
    expect(render({ dueTime: null }).text).toContain('All day');
  });

  it('shows the recurring tag only for a series todo', () => {
    expect(render().el.querySelector('app-recurring-tag')).toBeNull();
    TestBed.resetTestingModule();
    expect(render({ seriesId: 's1', ruleSummary: 'Every week' }).el.querySelector('app-recurring-tag')).not.toBeNull();
  });

  it('hides the description in compact density', () => {
    expect(render({}, 'compact').el.querySelector('.desc')).toBeNull();
  });

  it('emits toggleDone(true) from the checkbox and names it in aria-labels', () => {
    const { fixture, el } = render();
    const values: boolean[] = [];
    fixture.componentInstance.toggleDone.subscribe((v) => values.push(v));
    const check = el.querySelector<HTMLButtonElement>('.check')!;
    expect(check.getAttribute('aria-label')).toBe('Mark done: Buy groceries');
    expect(check.getAttribute('aria-pressed')).toBe('false');
    check.click();
    expect(values).toEqual([true]);
    expect(el.querySelector('.more')!.getAttribute('aria-label')).toBe('More actions for Buy groceries');
  });

  it('uses the not-done label when done', () => {
    const { el } = render({ done: true });
    expect(el.querySelector('.check')!.getAttribute('aria-label')).toBe('Mark not done: Buy groceries');
  });

  it('emits open from the title and remove from the menu Delete', () => {
    const { fixture, el } = render();
    let opened = 0;
    let removed = 0;
    fixture.componentInstance.open.subscribe(() => opened++);
    fixture.componentInstance.remove.subscribe(() => removed++);
    el.querySelector<HTMLButtonElement>('.title')!.click();
    expect(opened).toBe(1);

    el.querySelector<HTMLButtonElement>('.more')!.click();
    fixture.detectChanges();
    const items = Array.from(document.querySelectorAll<HTMLElement>('.todo-card-menu .p-menu-item-link'));
    items.find((i) => i.textContent?.includes('Delete'))!.click();
    expect(removed).toBe(1);
  });
});
