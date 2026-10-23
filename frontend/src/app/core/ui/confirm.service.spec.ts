import { TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { vi, expect, describe, it, beforeEach } from 'vitest';
import { ConfirmService } from './confirm.service';

describe('ConfirmService', () => {
  let service: ConfirmService;
  let confirmationService: ConfirmationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ConfirmService, ConfirmationService],
    });

    service = TestBed.inject(ConfirmService);
    confirmationService = TestBed.inject(ConfirmationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('resolves to true when accepted', async () => {
    let acceptCallback: (() => void) | undefined;

    vi.spyOn(confirmationService, 'confirm').mockImplementation((options) => {
      const opts = options as { accept: () => void };
      acceptCallback = opts.accept;
      return confirmationService;
    });

    const promise = service.confirmDanger({
      header: 'Delete?',
      message: 'Are you sure?',
      acceptLabel: 'Delete',
    });

    // Simulate user clicking accept
    acceptCallback?.();

    const result = await promise;
    expect(result).toBe(true);
  });

  it('resolves to false when rejected', async () => {
    let rejectCallback: (() => void) | undefined;

    vi.spyOn(confirmationService, 'confirm').mockImplementation((options) => {
      const opts = options as { reject: () => void };
      rejectCallback = opts.reject;
      return confirmationService;
    });

    const promise = service.confirmDanger({
      header: 'Delete?',
      message: 'Are you sure?',
      acceptLabel: 'Delete',
    });

    // Simulate user clicking reject
    rejectCallback?.();

    const result = await promise;
    expect(result).toBe(false);
  });

  it('passes danger styling to the dialog', () => {
    const confirmSpy = vi.spyOn(confirmationService, 'confirm');

    service.confirmDanger({
      header: 'Delete?',
      message: 'Are you sure?',
      acceptLabel: 'Delete',
      rejectLabel: 'Keep',
    });

    expect(confirmSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        header: 'Delete?',
        message: 'Are you sure?',
        acceptLabel: 'Delete',
        rejectLabel: 'Keep',
        icon: 'pi pi-exclamation-triangle',
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-text',
      }),
    );
  });

  it('uses default reject label when not provided', () => {
    const confirmSpy = vi.spyOn(confirmationService, 'confirm');

    service.confirmDanger({
      header: 'Delete?',
      message: 'Are you sure?',
      acceptLabel: 'Delete',
    });

    expect(confirmSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        rejectLabel: 'Cancel',
      }),
    );
  });
});
