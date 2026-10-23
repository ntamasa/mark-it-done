import { Injectable, inject } from '@angular/core';
import { ConfirmationService } from 'primeng/api';

export interface ConfirmDangerOptions {
  header: string;
  message: string;
  acceptLabel: string;
  rejectLabel?: string;
}

/**
 * Confirm dialog service that wraps PrimeNG's ConfirmationService.
 * Provides a promise-based API for destructive action confirmations.
 */
@Injectable({
  providedIn: 'root',
})
export class ConfirmService {
  private confirmationService = inject(ConfirmationService);

  /**
   * Show a danger-styled confirm dialog.
   * Returns a promise that resolves to true if accepted, false if rejected.
   */
  confirmDanger(opts: ConfirmDangerOptions): Promise<boolean> {
    return new Promise((resolve) => {
      this.confirmationService.confirm({
        header: opts.header,
        message: opts.message,
        icon: 'pi pi-exclamation-triangle',
        accept: () => resolve(true),
        reject: () => resolve(false),
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-text',
        acceptLabel: opts.acceptLabel,
        rejectLabel: opts.rejectLabel || 'Cancel',
      });
    });
  }
}
