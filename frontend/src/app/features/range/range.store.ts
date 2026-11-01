import { DestroyRef, Injectable, Injector, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import { addDays, fromIso, mondayOf, todayIso, toIso } from '../../core/dates/dates';
import { TodoApi } from '../../core/todos/todo.api';
import { TodoEvents } from '../../core/todos/todo-events';
import { Todo } from '../../core/todos/todo.models';

export interface RangeConfig {
  days: number;
  align: 'monday' | 'none';
  defaultStart: () => string;
  unitLabel: 'week' | '2 days';
}

export interface DayBucket {
  date: string;
  todos: Todo[];
  isToday: boolean;
}

/** True for a real calendar date written as YYYY-MM-DD. */
export function isValidIso(value: string | null | undefined): value is string {
  return !!value && /^\d{4}-\d{2}-\d{2}$/.test(value) && toIso(fromIso(value)) === value;
}

/**
 * State behind the week and 2-day views: the range start lives in the ?start= query param, the todos of the range
 * are loaded and grouped per day. Provide it in the page component, so each view has its own instance.
 */
@Injectable()
export class RangeStore {
  private readonly api = inject(TodoApi);
  private readonly events = inject(TodoEvents);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  private readonly config = signal<RangeConfig | null>(null);
  private readonly rawStart = signal<string | null>(null);
  private readonly todos = signal<Todo[]>([]);
  private readonly loaded = signal(false);
  private readonly errorSignal = signal<unknown>(null);

  /** The effective first day of the range (YYYY-MM-DD), or '' before init. */
  readonly start = computed(() => {
    const config = this.config();
    if (!config) {
      return '';
    }
    const raw = this.rawStart();
    const start = isValidIso(raw) ? raw : config.defaultStart();
    return config.align === 'monday' ? mondayOf(start) : start;
  });
  readonly end = computed(() => {
    const config = this.config();
    return config ? addDays(this.start(), config.days - 1) : '';
  });
  /** True only during the very first load; later reloads keep the old data visible. */
  readonly loading = computed(() => !!this.config() && !this.loaded() && this.errorSignal() === null);
  /** The last load error, or null. The old data stays in place. */
  readonly error = this.errorSignal.asReadonly();
  readonly containsToday = computed(() => {
    const today = todayIso();
    return !!this.config() && this.start() <= today && today <= this.end();
  });
  readonly days = computed<DayBucket[]>(() => {
    const config = this.config();
    if (!config) {
      return [];
    }
    const today = todayIso();
    const byDate = new Map<string, Todo[]>();
    for (const todo of this.todos()) {
      const list = byDate.get(todo.dueDate);
      if (list) {
        list.push(todo);
      } else {
        byDate.set(todo.dueDate, [todo]);
      }
    }
    const start = this.start();
    return Array.from({ length: config.days }, (_, i) => {
      const date = addDays(start, i);
      return { date, todos: byDate.get(date) ?? [], isToday: date === today };
    });
  });

  /** Starts watching the URL and loading data. Call once, in the page's injection context lifetime. */
  init(config: RangeConfig): void {
    if (this.config()) {
      return;
    }
    this.config.set(config);

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const raw = params.get('start');
      this.rawStart.set(raw);
      if (config.align === 'monday' && isValidIso(raw) && mondayOf(raw) !== raw) {
        void this.navigate(mondayOf(raw), true);
      }
    });

    const query = computed(() => ({ from: this.start(), to: this.end(), version: this.events.version() }));
    toObservable(query, { injector: this.injector })
      .pipe(
        switchMap(({ from, to }) =>
          this.api.range(from, to).pipe(
            map((todos) => ({ todos })),
            catchError((error: unknown) => of({ error })),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if ('todos' in result) {
          this.todos.set(result.todos);
          this.loaded.set(true);
          this.errorSignal.set(null);
        } else {
          this.errorSignal.set(result.error);
        }
      });
  }

  /** Moves the range by whole units (weeks or 2-day steps). */
  go(deltaUnits: number): void {
    const config = this.config();
    if (!config) {
      return;
    }
    void this.navigate(addDays(this.start(), deltaUnits * config.days), false);
  }

  goToday(): void {
    const config = this.config();
    if (!config) {
      return;
    }
    const start = config.defaultStart();
    void this.navigate(config.align === 'monday' ? mondayOf(start) : start, false);
  }

  /** Replaces a todo in the shown data (optimistic updates). */
  patch(todo: Todo): void {
    this.todos.update((list) => list.map((t) => (t.id === todo.id ? todo : t)));
  }

  private navigate(start: string, replaceUrl: boolean): Promise<boolean> {
    return this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { start },
      replaceUrl,
    });
  }
}
