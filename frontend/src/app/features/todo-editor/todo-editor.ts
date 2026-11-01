import { BreakpointObserver } from '@angular/cdk/layout';
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChildren,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FieldTree, FormField, disabled, form, maxLength, required, submit, validate } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DatePicker, DatePickerModule } from 'primeng/datepicker';
import { DrawerModule } from 'primeng/drawer';
import { Select, SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { firstValueFrom, map } from 'rxjs';
import { CategoryStore } from '../../core/categories/category.store';
import { fromIso, toIso } from '../../core/dates/dates';
import { toApiProblem } from '../../core/http/api-error';
import { TodoActions } from '../../core/todos/todo-actions';
import { TodoApi } from '../../core/todos/todo.api';
import { PRIORITY_LABEL, Priority, TodoRequest } from '../../core/todos/todo.models';
import { ConfirmService } from '../../core/ui/confirm.service';
import { NotifyService } from '../../core/ui/notify.service';
import { EditorState, TodoEditorService } from './todo-editor.service';

export const NAME_REQUIRED_MESSAGE = 'Name is required.';
export const DATE_REQUIRED_MESSAGE = 'Expiration date is required.';
export const DESKTOP_EDITOR_QUERY = '(min-width: 1024px)';
export const DEFAULT_TIME = '09:00';

const SWIPE_CLOSE_DISTANCE = 80;

export interface TodoEditorModel {
  name: string;
  description: string;
  dueDate: Date | null;
  withTime: boolean;
  time: Date | null;
  priority: Priority;
  /** Empty string means "No category". */
  categoryId: string;
}

const PRIORITY_ORDER: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];

/** A Date carrying only the given HH:mm (the date part is irrelevant for the time-only picker). */
function timeToDate(hhmm: string): Date {
  const [h, m] = hhmm.split(':').map(Number);
  return new Date(2000, 0, 1, h, m);
}

function dateToTime(d: Date): string {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export function initialModel(state: EditorState): TodoEditorModel {
  const todo = state.todo;
  if (todo) {
    return {
      name: todo.name,
      description: todo.description ?? '',
      dueDate: fromIso(todo.dueDate),
      withTime: todo.dueTime !== null,
      time: todo.dueTime ? timeToDate(todo.dueTime.slice(0, 5)) : null,
      priority: todo.priority,
      categoryId: todo.categoryId ?? '',
    };
  }
  return {
    name: '',
    description: '',
    dueDate: state.defaultDate ? fromIso(state.defaultDate) : null,
    withTime: false,
    time: null,
    priority: 'MEDIUM',
    categoryId: '',
  };
}

/** A comparable snapshot of the model; "dirty" means it differs from the snapshot taken when the editor opened. */
export function snapshotOf(m: TodoEditorModel): string {
  return JSON.stringify([
    m.name,
    m.description,
    m.dueDate ? toIso(m.dueDate) : null,
    m.withTime,
    m.withTime && m.time ? dateToTime(m.time) : null,
    m.priority,
    m.categoryId,
  ]);
}

/** Maps the form to the API request. dueDate is the local calendar date (no UTC shift). */
export function toRequest(m: TodoEditorModel): TodoRequest {
  return {
    name: m.name.trim(),
    description: m.description.trim() === '' ? null : m.description,
    dueDate: toIso(m.dueDate as Date),
    dueTime: m.withTime && m.time ? dateToTime(m.time) : null,
    priority: m.priority,
    categoryId: m.categoryId === '' ? null : m.categoryId,
  };
}

/**
 * The single create/edit todo surface: a PrimeNG drawer, bottom sheet below 1024px and right panel from 1024px up.
 * Open/close state, the edit target and focus restoring live in TodoEditorService.
 * PrimeNG controls (select, datepicker, toggleswitch) are ControlValueAccessors and bind directly to the signal form
 * through [formField]; no ngModel or reactive forms are used.
 */
@Component({
  selector: 'app-todo-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormField, ButtonModule, DatePickerModule, DrawerModule, SelectModule, TextareaModule, ToggleSwitchModule],
  templateUrl: './todo-editor.html',
  styleUrl: './todo-editor.scss',
})
export class TodoEditor {
  private readonly editor = inject(TodoEditorService);
  private readonly api = inject(TodoApi);
  private readonly actions = inject(TodoActions);
  private readonly confirm = inject(ConfirmService);
  private readonly notify = inject(NotifyService);
  private readonly categoryStore = inject(CategoryStore);
  private readonly doc = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly isDesktop = toSignal(
    inject(BreakpointObserver)
      .observe(DESKTOP_EDITOR_QUERY)
      .pipe(map((s) => s.matches)),
    { initialValue: false },
  );

  protected readonly state = this.editor.state;
  protected readonly open = computed(() => this.state() !== null);
  protected readonly isEdit = computed(() => this.state()?.mode === 'edit');
  protected readonly title = computed(() => (this.isEdit() ? 'Edit todo' : 'New todo'));

  protected readonly model = signal<TodoEditorModel>(initialModel({ mode: 'new', todo: null, defaultDate: null }));
  private readonly initialSnapshot = signal(snapshotOf(this.model()));

  protected readonly todoForm = form(this.model, (p) => {
    required(p.name, { message: NAME_REQUIRED_MESSAGE });
    validate(p.name, ({ value }) =>
      value().trim() === '' ? { kind: 'required', message: NAME_REQUIRED_MESSAGE } : undefined,
    );
    maxLength(p.name, 200, { message: 'Name must be at most 200 characters.' });
    maxLength(p.description, 2000, { message: 'Description must be at most 2000 characters.' });
    required(p.dueDate, { message: DATE_REQUIRED_MESSAGE });
    disabled(p.time, ({ valueOf }) => !valueOf(p.withTime));
  });

  protected readonly saving = signal(false);
  protected readonly submitAttempted = signal(false);
  private confirming = false;

  readonly dirty = computed(() => snapshotOf(this.model()) !== this.initialSnapshot());

  protected readonly priorityOptions = PRIORITY_ORDER.map((value) => ({ value, label: PRIORITY_LABEL[value] }));
  protected readonly categoryOptions = computed(() => [
    { value: '', label: 'No category' },
    ...this.categoryStore.categories().map((c) => ({ value: c.id, label: c.name })),
  ]);

  private readonly selects = viewChildren(Select);
  private readonly datepickers = viewChildren(DatePicker);

  private touchStartY: number | null = null;

  constructor() {
    effect(() => {
      const state = this.state();
      if (state) {
        untracked(() => this.init(state));
      }
    });
  }

  private init(state: EditorState): void {
    const next = initialModel(state);
    this.model.set(next);
    this.todoForm().reset();
    this.initialSnapshot.set(snapshotOf(next));
    this.submitAttempted.set(false);
    this.saving.set(false);
  }

  /** The first error message of a field, shown after blur or a submit attempt. */
  protected errorOf(field: FieldTree<unknown>): string | null {
    const state = field();
    if (!(state.touched() || this.submitAttempted())) {
      return null;
    }
    return state.errors()[0]?.message ?? null;
  }

  protected onTimeToggle(on: boolean): void {
    if (on && !this.model().time) {
      this.model.update((m) => ({ ...m, time: timeToDate(DEFAULT_TIME) }));
    }
  }

  /** Called when the drawer has opened: names the dialog and moves focus to Name. */
  protected onShow(): void {
    const container = this.doc.querySelector('.todo-editor-drawer');
    container?.setAttribute('role', 'dialog');
    container?.setAttribute('aria-modal', 'true');
    container?.setAttribute('aria-labelledby', 'todo-editor-title');
    const mask = container?.previousElementSibling;
    if (mask?.classList.contains('p-drawer-mask')) {
      mask.addEventListener('click', (event) => {
        if (event.target === mask) {
          void this.requestClose();
        }
      });
    }
    setTimeout(() => this.doc.getElementById('todo-name')?.focus());
  }

  protected onEscape(event: Event): void {
    // Only an overlay that is actually open counts; one that is still animating away does not.
    const overlayOpen = [...this.selects(), ...this.datepickers()].some((c) => c.overlayVisible());
    if (event.defaultPrevented || overlayOpen) {
      return;
    }
    // Keep PrimeNG's own Escape handling out of it: every close goes through requestClose.
    event.stopPropagation();
    event.preventDefault();
    void this.requestClose();
  }

  protected onTouchStart(event: TouchEvent): void {
    this.touchStartY = event.touches[0]?.clientY ?? null;
  }

  protected onTouchEnd(event: TouchEvent): void {
    const start = this.touchStartY;
    this.touchStartY = null;
    const end = event.changedTouches[0]?.clientY;
    if (!this.isDesktop() && start !== null && end !== undefined && end - start > SWIPE_CLOSE_DISTANCE) {
      void this.requestClose();
    }
  }

  /** After a declined discard the confirm dialog hands focus to the body; put it back inside the editor. */
  private restoreFocus(previous: Element | null): void {
    setTimeout(() => {
      const inside = previous instanceof HTMLElement && previous.isConnected && previous.closest('.todo-editor-drawer');
      (inside ? (previous as HTMLElement) : this.doc.getElementById('todo-name'))?.focus();
    }, 0);
  }

  /** Closes the editor, asking first when there are unsaved changes. Resolves to true when it closed. */
  async requestClose(): Promise<boolean> {
    if (!this.open() || this.confirming) {
      return false;
    }
    if (this.dirty()) {
      const previous = this.doc.activeElement;
      this.confirming = true;
      try {
        const discard = await this.confirm.confirmDanger({
          header: 'Discard changes?',
          message: 'Your changes will be lost.',
          acceptLabel: 'Discard',
        });
        if (!discard) {
          this.restoreFocus(previous);
          return false;
        }
      } finally {
        this.confirming = false;
      }
    }
    this.editor.close();
    return true;
  }

  async onSubmit(event?: Event): Promise<void> {
    event?.preventDefault();
    if (this.saving()) {
      return;
    }
    this.submitAttempted.set(true);
    await submit(this.todoForm, {
      action: async () => {
        await this.save();
      },
      onInvalid: () => this.focusFirstInvalid(),
    });
  }

  /** Leaves for the categories page, after the usual discard confirmation. */
  protected async manageCategories(event: Event): Promise<void> {
    event.preventDefault();
    if (await this.requestClose()) {
      await this.router.navigateByUrl('/categories');
    }
  }

  async onDelete(): Promise<void> {
    const todo = this.state()?.todo;
    if (!todo || this.saving()) {
      return;
    }
    if (await this.actions.confirmDelete(todo)) {
      this.editor.close();
    }
  }

  private focusFirstInvalid(): void {
    if (this.todoForm.name().invalid()) {
      this.doc.getElementById('todo-name')?.focus();
    } else if (this.todoForm.description().invalid()) {
      this.doc.getElementById('todo-description')?.focus();
    } else {
      this.host.nativeElement.ownerDocument.getElementById('todo-date')?.focus();
    }
  }

  private async save(): Promise<void> {
    const todo = this.state()?.todo;
    const req = toRequest(this.model());
    this.saving.set(true);
    try {
      // TodoApi notifies TodoEvents.changed() after a successful create/update.
      await firstValueFrom(todo ? this.api.update(todo.id, req) : this.api.create(req));
    } catch (err) {
      this.notify.error(toApiProblem(err));
      return;
    } finally {
      this.saving.set(false);
    }
    this.editor.close();
    this.notify.success(todo ? 'Todo updated' : 'Todo created');
  }
}
