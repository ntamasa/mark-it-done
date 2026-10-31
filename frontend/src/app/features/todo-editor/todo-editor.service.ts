import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Todo } from '../../core/todos/todo.models';

export interface EditorState {
  mode: 'new' | 'edit';
  todo: Todo | null;
  defaultDate: string | null;
  defaultScope?: 'THIS' | 'FUTURE';
  repeat?: boolean;
}

@Injectable({ providedIn: 'root' })
export class TodoEditorService {
  private readonly doc = inject(DOCUMENT);
  private readonly stateSignal = signal<EditorState | null>(null);
  private opener: HTMLElement | null = null;

  readonly state = this.stateSignal.asReadonly();

  openNew(defaultDate?: string, opts?: { repeat?: boolean }): void {
    this.rememberOpener();
    this.stateSignal.set({ mode: 'new', todo: null, defaultDate: defaultDate ?? null, repeat: opts?.repeat });
  }

  openEdit(todo: Todo, opts?: { defaultScope?: 'THIS' | 'FUTURE' }): void {
    this.rememberOpener();
    this.stateSignal.set({ mode: 'edit', todo, defaultDate: todo.dueDate, defaultScope: opts?.defaultScope });
  }

  /** Closes the editor and restores focus to the element that opened it. */
  close(): void {
    this.stateSignal.set(null);
    const el = this.opener;
    this.opener = null;
    if (el?.isConnected) {
      el.focus();
    }
  }

  private rememberOpener(): void {
    const active = this.doc.activeElement;
    this.opener = active instanceof HTMLElement && active !== this.doc.body ? active : null;
  }
}
