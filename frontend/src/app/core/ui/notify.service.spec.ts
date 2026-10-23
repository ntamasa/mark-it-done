import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { vi } from 'vitest';
import { NotifyService } from './notify.service';
import { ApiProblem } from '../http/api-error';

describe('NotifyService', () => {
  let service: NotifyService;
  let messageService: MessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [NotifyService, MessageService],
    });

    service = TestBed.inject(NotifyService);
    messageService = TestBed.inject(MessageService);

    vi.spyOn(messageService, 'add');
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('shows a success toast with 4s duration', () => {
    service.success('Test success');

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'Test success',
      life: 4000,
    });
  });

  it('shows an info toast with 4s duration', () => {
    service.info('Test info');

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'info',
      summary: 'Test info',
      life: 4000,
    });
  });

  it('shows an error toast with 6s duration and problem detail', () => {
    const problem: ApiProblem = {
      status: 400,
      code: 'VALIDATION_FAILED',
      detail: 'Field is required',
      errors: [],
    };

    service.error(problem);

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'Field is required',
      life: 6000,
    });
  });
});
