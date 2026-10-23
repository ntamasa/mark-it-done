import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ApiProblem } from '../http/api-error';

/**
 * Toast notification service that wraps PrimeNG's MessageService.
 * Displays success, info, and error messages with configurable durations.
 */
@Injectable({
  providedIn: 'root',
})
export class NotifyService {
  private messageService = inject(MessageService);

  /**
   * Show a success toast that dismisses after 4 seconds.
   */
  success(summary: string): void {
    this.messageService.add({
      severity: 'success',
      summary,
      life: 4000,
    });
  }

  /**
   * Show an info toast that dismisses after 4 seconds.
   */
  info(summary: string): void {
    this.messageService.add({
      severity: 'info',
      summary,
      life: 4000,
    });
  }

  /**
   * Show an error toast that dismisses after 6 seconds.
   */
  error(problem: ApiProblem): void {
    this.messageService.add({
      severity: 'error',
      summary: problem.detail,
      life: 6000,
    });
  }
}
