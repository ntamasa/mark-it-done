import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, ParamMap } from '@angular/router';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { TodoApi } from '../../core/todos/todo.api';
import { TodoEvents } from '../../core/todos/todo-events';
import { Todo } from '../../core/todos/todo.models';
import { buildRangePager } from './range-pager';
import { RangeConfig, RangeStore } from './range.store';

function todo(id: string, dueDate: string): Todo {
  return {
    id,
    name: id,
    description: null,
    dueDate,
    dueTime: null,
    priority: 'MEDIUM',
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
}

const week: RangeConfig = { days: 7, align: 'monday', defaultStart: () => '2026-10-26', unitLabel: 'week' };
const twoDays: RangeConfig = { days: 2, align: 'none', defaultStart: () => '2026-10-28', unitLabel: '2 days' };

describe('RangeStore', () => {
  let params$: BehaviorSubject<ParamMap>;
  let navigate: ReturnType<typeof vi.fn>;
  let range: ReturnType<typeof vi.fn>;
  let events: TodoEvents;

  function setup(
    start: string | null,
    config: RangeConfig,
    rangeImpl?: (from: string, to: string) => Observable<Todo[]>,
  ) {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 28, 10));
    params$ = new BehaviorSubject(convertToParamMap(start ? { start } : {}));
    navigate = vi.fn().mockResolvedValue(true);
    range = vi.fn(rangeImpl ?? (() => of([])));
    TestBed.configureTestingModule({
      providers: [
        RangeStore,
        { provide: Router, useValue: { navigate } },
        { provide: ActivatedRoute, useValue: { queryParamMap: params$ } },
        { provide: TodoApi, useValue: { range } },
      ],
    });
    events = TestBed.inject(TodoEvents);
    const store = TestBed.inject(RangeStore);
    store.init(config);
    TestBed.tick();
    return store;
  }

  afterEach(() => vi.useRealTimers());

  it('corrects a non-Monday start to its Monday, replacing the history entry', () => {
    const store = setup('2026-10-21', week);
    expect(store.start()).toBe('2026-10-19');
    expect(store.end()).toBe('2026-10-25');
    expect(navigate).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { start: '2026-10-19' }, replaceUrl: true }),
    );
    expect(range).toHaveBeenCalledWith('2026-10-19', '2026-10-25');
  });

  it('uses the default for a missing or invalid start without navigating', () => {
    expect(setup(null, week).start()).toBe('2026-10-26');
    TestBed.resetTestingModule();
    const store = setup('2026-13-45', week);
    expect(store.start()).toBe('2026-10-26');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('keeps any 2-day start', () => {
    const store = setup('2026-10-21', twoDays);
    expect(store.start()).toBe('2026-10-21');
    expect(store.end()).toBe('2026-10-22');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('go moves by units and goToday returns to the default', () => {
    const store = setup('2026-10-19', week);
    store.go(1);
    expect(navigate).toHaveBeenLastCalledWith(
      [],
      expect.objectContaining({ queryParams: { start: '2026-10-26' }, replaceUrl: false }),
    );
    store.go(-1);
    expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { start: '2026-10-12' } }));
    store.goToday();
    expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { start: '2026-10-26' } }));
  });

  it('go moves a 2-day range by 2 days', () => {
    const store = setup('2026-10-28', twoDays);
    store.go(1);
    expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { start: '2026-10-30' } }));
  });

  it('buckets todos per date in API order and marks today', () => {
    const store = setup('2026-10-26', week, () =>
      of([todo('b', '2026-10-28'), todo('a', '2026-10-28'), todo('c', '2026-10-30')]),
    );
    const days = store.days();
    expect(days.map((d) => d.date)).toEqual([
      '2026-10-26',
      '2026-10-27',
      '2026-10-28',
      '2026-10-29',
      '2026-10-30',
      '2026-10-31',
      '2026-11-01',
    ]);
    expect(days[2].todos.map((t) => t.id)).toEqual(['b', 'a']);
    expect(days[4].todos.map((t) => t.id)).toEqual(['c']);
    expect(days.filter((d) => d.isToday).map((d) => d.date)).toEqual(['2026-10-28']);
    expect(store.containsToday()).toBe(true);
  });

  it('reloads on a todo change and when the start changes, cancelling the stale request', () => {
    const pending: Subject<Todo[]>[] = [];
    const unsubscribed: boolean[] = [];
    const store = setup('2026-10-19', week, () => {
      const subject = new Subject<Todo[]>();
      const index = pending.push(subject) - 1;
      return new Observable<Todo[]>((subscriber) => {
        const sub = subject.subscribe(subscriber);
        return () => {
          unsubscribed[index] = true;
          sub.unsubscribe();
        };
      });
    });
    expect(range).toHaveBeenCalledTimes(1);
    expect(store.loading()).toBe(true);

    params$.next(convertToParamMap({ start: '2026-10-26' }));
    TestBed.tick();
    expect(range).toHaveBeenCalledTimes(2);
    expect(unsubscribed[0]).toBe(true);

    pending[1].next([todo('x', '2026-10-27')]);
    expect(store.loading()).toBe(false);

    events.changed();
    TestBed.tick();
    expect(range).toHaveBeenCalledTimes(3);
    // old data stays while reloading
    expect(store.loading()).toBe(false);
    expect(store.days()[1].todos).toHaveLength(1);
  });

  it('keeps old data and exposes an error when a reload fails', () => {
    let fail = false;
    const store = setup('2026-10-26', week, () =>
      fail ? throwError(() => new Error('boom')) : of([todo('a', '2026-10-27')]),
    );
    fail = true;
    events.changed();
    TestBed.tick();
    expect(store.error()).toBeTruthy();
    expect(store.days()[1].todos).toHaveLength(1);
    expect(range).toHaveBeenCalledTimes(2);
  });

  it('patch replaces a todo locally', () => {
    const store = setup('2026-10-26', week, () => of([todo('a', '2026-10-27')]));
    store.patch({ ...todo('a', '2026-10-27'), done: true });
    expect(store.days()[1].todos[0].done).toBe(true);
  });
});

describe('buildRangePager', () => {
  function fake(start: string, end: string, containsToday: boolean) {
    const store = {
      start: signal(start),
      end: signal(end),
      containsToday: signal(containsToday),
      go: vi.fn(),
      goToday: vi.fn(),
    };
    return store as unknown as RangeStore & { go: ReturnType<typeof vi.fn>; goToday: ReturnType<typeof vi.fn> };
  }

  it('labels the week range', () => {
    const p = buildRangePager(fake('2026-10-19', '2026-10-25', false), week, '2026-10-28');
    expect(p.label).toBe('Oct 19 – Oct 25');
    expect(p.prevLabel).toBe('Previous week');
    expect(p.nextLabel).toBe('Next week');
    expect(p.todayDisabled).toBe(false);
  });

  it('adds years when they differ', () => {
    expect(buildRangePager(fake('2025-12-29', '2026-01-04', false), week, '2026-10-28').label).toBe(
      'Dec 29, 2025 – Jan 4, 2026',
    );
  });

  it('says Today & tomorrow for a 2-day range starting today', () => {
    const store = fake('2026-10-28', '2026-10-29', true);
    const p = buildRangePager(store, twoDays, '2026-10-28');
    expect(p.label).toBe('Today & tomorrow');
    expect(p.prevLabel).toBe('Previous 2 days');
    expect(p.nextLabel).toBe('Next 2 days');
    expect(p.todayDisabled).toBe(true);
    p.next();
    p.prev();
    p.today();
    expect(store.go).toHaveBeenNthCalledWith(1, 1);
    expect(store.go).toHaveBeenNthCalledWith(2, -1);
    expect(store.goToday).toHaveBeenCalled();
  });

  it('uses the date range for a 2-day range not starting today', () => {
    expect(buildRangePager(fake('2026-10-30', '2026-10-31', false), twoDays, '2026-10-28').label).toBe(
      'Oct 30 – Oct 31',
    );
  });
});
