import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TodoApi } from './todo.api';
import { TodoEvents } from './todo-events';
import { Todo, TodoRequest } from './todo.models';

const todo: Todo = {
  id: 't1',
  name: 'Pay rent',
  description: null,
  dueDate: '2026-10-21',
  dueTime: null,
  priority: 'HIGH',
  categoryId: null,
  done: false,
  doneAt: null,
  createdAt: '2026-10-01T10:00:00Z',
  updatedAt: '2026-10-01T10:00:00Z',
  expired: false,
  seriesId: null,
  occurrenceDate: null,
  ruleSummary: null,
};

const req: TodoRequest = { name: 'Pay rent', dueDate: '2026-10-21', priority: 'HIGH' };

describe('TodoApi', () => {
  let api: TodoApi;
  let http: HttpTestingController;
  let events: TodoEvents;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TodoApi);
    http = TestBed.inject(HttpTestingController);
    events = TestBed.inject(TodoEvents);
  });

  afterEach(() => http.verify());

  it('range requests GET /api/todos with from and to', () => {
    let result: Todo[] | undefined;
    api.range('2026-10-19', '2026-10-25').subscribe((list) => (result = list));
    const req = http.expectOne('/api/todos?from=2026-10-19&to=2026-10-25');
    expect(req.request.method).toBe('GET');
    req.flush([todo]);
    expect(result).toEqual([todo]);
  });

  it('range does not notify change listeners', () => {
    api.range('2026-10-19', '2026-10-25').subscribe();
    http.expectOne('/api/todos?from=2026-10-19&to=2026-10-25').flush([]);
    expect(events.version()).toBe(0);
  });

  it('get requests GET /api/todos/{id}', () => {
    let result: Todo | undefined;
    api.get('t1').subscribe((t) => (result = t));
    const r = http.expectOne('/api/todos/t1');
    expect(r.request.method).toBe('GET');
    r.flush(todo);
    expect(result).toEqual(todo);
  });

  it('create posts the body and notifies on success', () => {
    let result: Todo | undefined;
    api.create(req).subscribe((t) => (result = t));
    const r = http.expectOne('/api/todos');
    expect(r.request.method).toBe('POST');
    expect(r.request.body).toEqual(req);
    expect(events.version()).toBe(0);
    r.flush(todo);
    expect(result).toEqual(todo);
    expect(events.version()).toBe(1);
  });

  it('update puts the body to the todo URL and notifies on success', () => {
    api.update('t1', req).subscribe();
    const r = http.expectOne('/api/todos/t1');
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual(req);
    r.flush(todo);
    expect(events.version()).toBe(1);
  });

  it('setDone patches { done } to the done URL and notifies on success', () => {
    api.setDone('t1', true).subscribe();
    const r = http.expectOne('/api/todos/t1/done');
    expect(r.request.method).toBe('PATCH');
    expect(r.request.body).toEqual({ done: true });
    r.flush({ ...todo, done: true });
    expect(events.version()).toBe(1);
  });

  it('delete sends DELETE to the todo URL and notifies on success', () => {
    api.delete('t1').subscribe();
    const r = http.expectOne('/api/todos/t1');
    expect(r.request.method).toBe('DELETE');
    r.flush(null, { status: 204, statusText: 'No Content' });
    expect(events.version()).toBe(1);
  });

  it('a failed change does not notify change listeners', () => {
    let failed = false;
    api.delete('t1').subscribe({ error: () => (failed = true) });
    http.expectOne('/api/todos/t1').flush({}, { status: 404, statusText: 'Not Found' });
    expect(failed).toBe(true);
    expect(events.version()).toBe(0);
  });
});
