import { Injectable, signal } from '@angular/core';

export interface Pager {
  label: string;
  prevLabel: string;
  nextLabel: string;
  todayDisabled: boolean;
  prev(): void;
  next(): void;
  today(): void;
}

/** The current view's title, pager and focused date; views set them and clear them on destroy. */
@Injectable({ providedIn: 'root' })
export class ShellHeader {
  readonly title = signal('');
  readonly pager = signal<Pager | null>(null);
  readonly focusedDate = signal<string | null>(null);
}
