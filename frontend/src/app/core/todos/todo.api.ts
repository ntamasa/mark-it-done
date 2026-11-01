import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { DoneRequest, Todo, TodoRequest } from './todo.models';
import { TodoEvents } from './todo-events';

/** Client for the todo endpoints. Every successful change notifies TodoEvents. */
@Injectable({ providedIn: 'root' })
export class TodoApi {
  private readonly http = inject(HttpClient);
  private readonly events = inject(TodoEvents);

  /** Todos due between from and to (inclusive), both YYYY-MM-DD. */
  range(from: string, to: string): Observable<Todo[]> {
    return this.http.get<Todo[]>('/api/todos', { params: { from, to } });
  }

  get(id: string): Observable<Todo> {
    return this.http.get<Todo>(`/api/todos/${id}`);
  }

  create(req: TodoRequest): Observable<Todo> {
    return this.http.post<Todo>('/api/todos', req).pipe(tap(() => this.events.changed()));
  }

  update(id: string, req: TodoRequest): Observable<Todo> {
    return this.http.put<Todo>(`/api/todos/${id}`, req).pipe(tap(() => this.events.changed()));
  }

  setDone(id: string, done: boolean): Observable<Todo> {
    const body: DoneRequest = { done };
    return this.http.patch<Todo>(`/api/todos/${id}/done`, body).pipe(tap(() => this.events.changed()));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/todos/${id}`).pipe(tap(() => this.events.changed()));
  }
}
