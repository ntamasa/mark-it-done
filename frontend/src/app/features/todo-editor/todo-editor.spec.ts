import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { CategoryStore } from '../../core/categories/category.store';
import { TodoActions } from '../../core/todos/todo-actions';
import { TodoEvents } from '../../core/todos/todo-events';
import { TodoApi } from '../../core/todos/todo.api';
import { Todo } from '../../core/todos/todo.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { NotifyService } from '../../core/ui/notify.service';
import { NAME_REQUIRED_MESSAGE, TodoEditor, initialModel, snapshotOf, toRequest } from './todo-editor';
import { TodoEditorService } from './todo-editor.service';

const todo: Todo = {
  id: 't1',
  name: 'Dentist',
  description: 'Bring x-rays',
  dueDate: '2026-10-21',
  dueTime: '14:30:00',
  priority: 'HIGH',
  categoryId: 'c1',
  done: false,
  doneAt: null,
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  expired: false,
  seriesId: null,
  occurrenceDate: null,
  ruleSummary: null,
};

describe('TodoEditor', () => {
  const create = vi.fn();
  const update = vi.fn();
  const confirmDanger = vi.fn();
  const confirmDelete = vi.fn();
  const success = vi.fn();
  const error = vi.fn();
  let service: TodoEditorService;
  let events: TodoEvents;
  let fixture: ComponentFixture<TodoEditor>;
  let component: TodoEditor;
  let desktop$: BehaviorSubject<{ matches: boolean }>;

  function setup(matches = true) {
    for (const fn of [create, update, confirmDanger, confirmDelete, success, error]) {
      fn.mockReset();
    }
    create.mockReturnValue(of(todo));
    update.mockReturnValue(of(todo));
    desktop$ = new BehaviorSubject({ matches });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: TodoApi, useValue: { create, update } },
        { provide: ConfirmService, useValue: { confirmDanger } },
        { provide: TodoActions, useValue: { confirmDelete } },
        { provide: NotifyService, useValue: { success, error } },
        {
          provide: CategoryStore,
          useValue: { categories: signal([{ id: 'c1', name: 'Work', swatch: 'TEAL', itemCount: 0 }]) },
        },
        { provide: BreakpointObserver, useValue: { observe: () => desktop$ } },
      ],
    });
    service = TestBed.inject(TodoEditorService);
    events = TestBed.inject(TodoEvents);
    fixture = TestBed.createComponent(TodoEditor);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  async function open(action: () => void) {
    action();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const q = <T extends HTMLElement>(sel: string) => document.body.querySelector<T>(sel);

  function type(sel: string, value: string) {
    const el = q<HTMLInputElement>(sel)!;
    el.value = value;
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function submit() {
    q('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => {
    fixture.destroy();
    document.body.innerHTML = '';
  });

  it('new mode defaults to Medium priority, the given date and no time', () => {
    setup();
    service.openNew('2026-10-21');
    TestBed.tick();
    expect(snapshotOf(component['model']())).toBe(snapshotOf(initialModel(service.state()!)));
    const req = toRequest({ ...component['model'](), name: 'x' });
    expect(req).toEqual({
      name: 'x',
      description: null,
      dueDate: '2026-10-21',
      dueTime: null,
      priority: 'MEDIUM',
      categoryId: null,
    });
  });

  it('renders the drawer with the title, the close button and the repeat slot placeholder', async () => {
    setup();
    await open(() => service.openNew('2026-10-21'));
    expect(q('#todo-editor-title')?.textContent).toBe('New todo');
    expect(q('button[aria-label="Close"]')).not.toBeNull();
    expect(q('.p-drawer-right')).not.toBeNull();
    expect(q('.drag-handle')).toBeNull();
    expect(Array.from(document.querySelectorAll('footer button')).map((b) => b.textContent?.trim())).toEqual([
      'Cancel',
      'Save',
    ]);
  });

  it('renders as a bottom sheet with a drag handle below 1024px', async () => {
    setup(false);
    await open(() => service.openNew('2026-10-21'));
    expect(q('.p-drawer-bottom')).not.toBeNull();
    expect(q('.drag-handle')).not.toBeNull();
  });

  it('saving with an empty name shows "Name is required." and makes no API call', async () => {
    setup();
    await open(() => service.openNew('2026-10-21'));
    expect(q('#todo-name-error')).toBeNull();
    await submit();
    expect(q('#todo-name-error')?.textContent?.trim()).toBe(NAME_REQUIRED_MESSAGE);
    expect(q('#todo-name')?.getAttribute('aria-invalid')).toBe('true');
    expect(create).not.toHaveBeenCalled();
    expect(update).not.toHaveBeenCalled();
  });

  it('with the time toggle off the request has no time', async () => {
    setup();
    await open(() => service.openNew('2026-10-21'));
    type('#todo-name', 'Buy milk');
    await submit();
    expect(create).toHaveBeenCalledTimes(1);
    expect(create.mock.calls[0][0]).toEqual({
      name: 'Buy milk',
      description: null,
      dueDate: '2026-10-21',
      dueTime: null,
      priority: 'MEDIUM',
      categoryId: null,
    });
  });

  it('sends HH:mm only when "Add time" is on', () => {
    setup();
    const base = initialModel({ mode: 'edit', todo, defaultDate: todo.dueDate });
    expect(toRequest(base).dueTime).toBe('14:30');
    expect(toRequest({ ...base, withTime: false }).dueTime).toBeNull();
  });

  it('edit mode pre-fills from a todo with time 14:30:00', async () => {
    setup();
    await open(() => service.openEdit(todo));
    expect(q('#todo-editor-title')?.textContent).toBe('Edit todo');
    expect(q<HTMLInputElement>('#todo-name')?.value).toBe('Dentist');
    expect(q<HTMLTextAreaElement>('#todo-description')?.value).toBe('Bring x-rays');
    const model = component['model']();
    expect(model.withTime).toBe(true);
    expect(model.time?.getHours()).toBe(14);
    expect(model.time?.getMinutes()).toBe(30);
    expect(model.priority).toBe('HIGH');
    expect(model.categoryId).toBe('c1');
    expect(q('footer')?.textContent).toContain('Delete');
    expect(component.dirty()).toBe(false);
  });

  it('uses the local calendar date, not the UTC date', () => {
    setup();
    const base = initialModel({ mode: 'new', todo: null, defaultDate: '2026-03-01' });
    expect(toRequest({ ...base, name: 'x' }).dueDate).toBe('2026-03-01');
  });

  it('binds the PrimeNG controls to the signal form', async () => {
    setup();
    await open(() => service.openEdit(todo));
    expect(q<HTMLInputElement>('#todo-date')?.value).toBe('Wed, Oct 21, 2026');
    expect(q<HTMLInputElement>('#todo-time')?.value).toBe('14:30');
    const toggle = q<HTMLInputElement>('.p-toggleswitch-input')!;
    expect(toggle.checked).toBe(true);
    toggle.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component['model']().withTime).toBe(false);
    expect(q<HTMLInputElement>('#todo-time')?.disabled).toBe(true);
  });

  it('closing with unsaved changes asks "Discard changes?"', async () => {
    setup();
    confirmDanger.mockResolvedValue(true);
    await open(() => service.openNew('2026-10-21'));
    type('#todo-name', 'Something');
    expect(component.dirty()).toBe(true);
    await component.requestClose();
    expect(confirmDanger).toHaveBeenCalledWith({
      header: 'Discard changes?',
      message: 'Your changes will be lost.',
      acceptLabel: 'Discard',
    });
    expect(service.state()).toBeNull();
  });

  it('keeps the editor open when the discard is declined', async () => {
    setup();
    confirmDanger.mockResolvedValue(false);
    await open(() => service.openNew('2026-10-21'));
    type('#todo-name', 'Something');
    expect(await component.requestClose()).toBe(false);
    expect(service.state()).not.toBeNull();
  });

  it('closing a clean form does not ask', async () => {
    setup();
    await open(() => service.openNew('2026-10-21'));
    await component.requestClose();
    expect(confirmDanger).not.toHaveBeenCalled();
    expect(service.state()).toBeNull();
  });

  it('Escape closes through the discard confirm when dirty', async () => {
    setup();
    confirmDanger.mockResolvedValue(false);
    await open(() => service.openNew('2026-10-21'));
    type('#todo-name', 'Something');
    q('#todo-name')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    await fixture.whenStable();
    expect(confirmDanger).toHaveBeenCalledTimes(1);
  });

  it('declining the discard puts focus back on the trigger', async () => {
    setup();
    confirmDanger.mockResolvedValue(false);
    await open(() => service.openNew('2026-10-21'));
    type('#todo-name', 'Something');
    const close = q<HTMLButtonElement>('button[aria-label="Close"]')!;
    close.focus();
    await component.requestClose();
    await new Promise((r) => setTimeout(r));
    expect(document.activeElement).toBe(close);
  });

  it('declining the discard falls back to Name when the trigger is outside the editor', async () => {
    setup();
    confirmDanger.mockResolvedValue(false);
    await open(() => service.openNew('2026-10-21'));
    type('#todo-name', 'Something');
    (document.activeElement as HTMLElement | null)?.blur();
    await component.requestClose();
    await new Promise((r) => setTimeout(r));
    expect(document.activeElement).toBe(q('#todo-name'));
  });

  it('Escape closes right after a select overlay is no longer visible, but not while it is open', async () => {
    setup();
    await open(() => service.openNew('2026-10-21'));
    // jsdom has no matchMedia, which the opening overlay needs.
    window.matchMedia ??= (() => ({ matches: false, addEventListener: () => undefined })) as never;
    const select = component['selects']()[0];
    const esc = () =>
      q('#todo-name')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    select.overlayVisible.set(true);
    fixture.detectChanges();
    esc();
    await fixture.whenStable();
    expect(service.state()).not.toBeNull();
    // The overlay element may linger in the DOM while it animates away; only the open state matters.
    select.overlayVisible.set(false);
    fixture.detectChanges();
    document.body.insertAdjacentHTML('beforeend', '<div class="md-overlay"></div>');
    esc();
    await fixture.whenStable();
    expect(service.state()).toBeNull();
  });

  it('a successful save notifies the views once, closes and shows the toast', async () => {
    setup();
    // The real TodoApi emits changed() after a successful save; model that here.
    create.mockImplementation(() => {
      events.changed();
      return of(todo);
    });
    await open(() => service.openNew('2026-10-21'));
    type('#todo-name', 'Buy milk');
    await submit();
    expect(events.version()).toBe(1);
    expect(success).toHaveBeenCalledWith('Todo created');
    expect(service.state()).toBeNull();
  });

  it('updates an existing todo and toasts "Todo updated"', async () => {
    setup();
    await open(() => service.openEdit(todo));
    type('#todo-name', 'Dentist 2');
    await submit();
    expect(update).toHaveBeenCalledTimes(1);
    expect(update.mock.calls[0][0]).toBe('t1');
    expect(update.mock.calls[0][1]).toMatchObject({ name: 'Dentist 2', dueTime: '14:30', categoryId: 'c1' });
    expect(success).toHaveBeenCalledWith('Todo updated');
  });

  it('deleting from the editor closes it without a discard prompt', async () => {
    setup();
    confirmDelete.mockResolvedValue(true);
    await open(() => service.openEdit(todo));
    type('#todo-name', 'changed');
    await component.onDelete();
    expect(confirmDelete).toHaveBeenCalledWith(todo);
    expect(confirmDanger).not.toHaveBeenCalled();
    expect(service.state()).toBeNull();
  });
});
