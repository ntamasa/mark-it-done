import { Injectable, Signal, signal } from '@angular/core';

/** Change notification for todos. The version increments on every todo change, so views can reload when it moves. */
@Injectable({ providedIn: 'root' })
export class TodoEvents {
  private readonly versionSignal = signal(0);

  readonly version: Signal<number> = this.versionSignal.asReadonly();

  changed(): void {
    this.versionSignal.update((v) => v + 1);
  }
}
