import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Category, CategoryRequest } from './category.models';

const byName = (a: Category, b: Category): number =>
  a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }) || a.id.localeCompare(b.id);

/** Single source of truth for the signed-in user's categories. */
@Injectable({ providedIn: 'root' })
export class CategoryStore {
  private readonly http = inject(HttpClient);
  private readonly state = signal<Category[]>([]);

  readonly categories = this.state.asReadonly();
  readonly byId = computed(() => new Map(this.state().map((c) => [c.id, c])));

  load(): void {
    this.http.get<Category[]>('/api/categories').subscribe({
      next: (list) => this.state.set([...list].sort(byName)),
      // Keep the current state on failure; the next load retries.
      error: () => undefined,
    });
  }

  create(req: CategoryRequest): Observable<Category> {
    return this.http
      .post<Category>('/api/categories', req)
      .pipe(tap((created) => this.state.update((list) => [...list, created].sort(byName))));
  }

  update(id: string, req: CategoryRequest): Observable<Category> {
    return this.http
      .put<Category>(`/api/categories/${id}`, req)
      .pipe(tap((saved) => this.state.update((list) => list.map((c) => (c.id === id ? saved : c)).sort(byName))));
  }

  /** Wired to the delete flow in MAR-69; intentionally empty for now. */
  requestDelete(category: Category): void {
    void category;
  }
}
