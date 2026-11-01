import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { of, throwError } from 'rxjs';
import { Category } from '../../core/categories/category.models';
import { CategoryStore } from '../../core/categories/category.store';
import { CategoryDialog, NAME_REQUIRED_MESSAGE, NAME_TAKEN_MESSAGE } from './category-dialog';

describe('CategoryDialog', () => {
  const existing: Category = { id: 'w', name: 'Work', swatch: 'TEAL', itemCount: 2 };
  const create = vi.fn();
  const update = vi.fn();
  let fixture: ComponentFixture<CategoryDialog>;
  let el: HTMLElement;
  let messages: MessageService;
  let closed: ReturnType<typeof vi.fn<() => void>>;

  async function setup(category: Category | null = null) {
    create.mockReset();
    update.mockReset();
    TestBed.configureTestingModule({
      providers: [MessageService, { provide: CategoryStore, useValue: { create, update } }],
    });
    messages = TestBed.inject(MessageService);
    vi.spyOn(messages, 'add');
    fixture = TestBed.createComponent(CategoryDialog);
    fixture.componentRef.setInput('category', category);
    closed = vi.fn<() => void>();
    fixture.componentInstance.closed.subscribe(closed);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    el = fixture.nativeElement;
  }

  const input = () => el.querySelector<HTMLInputElement>('#cat-name')!;
  const error = () => el.querySelector('#cat-name-error')?.textContent?.trim() ?? null;

  function type(value: string) {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function submit() {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders 10 swatches and the create title', async () => {
    await setup();
    expect(el.querySelector('#catedit-title')?.textContent).toBe('New category');
    expect(el.querySelectorAll('[role="radio"]').length).toBe(10);
    expect(el.querySelector('[role="radio"][aria-checked="true"]')?.getAttribute('aria-label')).toBe('Teal');
  });

  it('requires a name and does not call the store', async () => {
    await setup();
    expect(error()).toBeNull();
    await submit();
    expect(error()).toBe(NAME_REQUIRED_MESSAGE);
    expect(input().getAttribute('aria-invalid')).toBe('true');
    expect(create).not.toHaveBeenCalled();
  });

  it('rejects a whitespace-only name', async () => {
    await setup();
    type('   ');
    await submit();
    expect(error()).toBe(NAME_REQUIRED_MESSAGE);
    expect(create).not.toHaveBeenCalled();
  });

  it('creates with the chosen swatch and shows a toast', async () => {
    await setup();
    create.mockReturnValue(of({ id: 'n', name: 'Gym', swatch: 'SKY', itemCount: 0 }));
    type(' Gym ');
    el.querySelector<HTMLElement>('[aria-label="Sky"]')!.click();
    fixture.detectChanges();
    expect(el.querySelector('.preview app-category-chip')?.textContent?.trim()).toBe('Gym');
    await submit();
    expect(create).toHaveBeenCalledWith({ name: 'Gym', swatch: 'SKY' });
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Category created' }));
    expect(closed).toHaveBeenCalled();
  });

  it('edits an existing category', async () => {
    await setup(existing);
    expect(el.querySelector('#catedit-title')?.textContent).toBe('Edit category');
    expect(input().value).toBe('Work');
    update.mockReturnValue(of({ ...existing, name: 'Job' }));
    type('Job');
    await submit();
    expect(update).toHaveBeenCalledWith('w', { name: 'Job', swatch: 'TEAL' });
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Category updated' }));
  });

  it('shows the conflict message on 409 and stays open', async () => {
    await setup();
    create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { code: 'CATEGORY_NAME_TAKEN', detail: 'dup' } })),
    );
    type('Work');
    await submit();
    expect(error()).toBe(NAME_TAKEN_MESSAGE);
    expect(closed).not.toHaveBeenCalled();
  });

  it('closes on Escape', async () => {
    await setup();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(closed).toHaveBeenCalled();
  });

  it('is labelled by its own title', async () => {
    await setup();
    const dialog = el.querySelector('[role="dialog"]')!;
    const id = dialog.getAttribute('aria-labelledby')!;
    expect(id).toBe('catedit-title');
    expect(el.querySelector('#' + id)?.textContent).toBe('New category');
  });

  it('uses a roving tabindex and arrow keys move focus and selection with wrap-around', async () => {
    await setup();
    const radios = () => Array.from(el.querySelectorAll<HTMLElement>('[role="radio"]'));
    expect(radios().filter((r) => r.getAttribute('tabindex') === '0').length).toBe(1);
    expect(radios()[0].getAttribute('tabindex')).toBe('0');

    const press = (key: string) => {
      radios()
        .find((r) => r.getAttribute('tabindex') === '0')!
        .dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };
    press('ArrowRight');
    expect(radios()[1].getAttribute('aria-checked')).toBe('true');
    expect(radios()[1].getAttribute('tabindex')).toBe('0');
    expect(document.activeElement).toBe(radios()[1]);
    press('ArrowLeft');
    press('ArrowLeft');
    expect(radios()[9].getAttribute('aria-checked')).toBe('true');
    press('ArrowDown');
    expect(radios()[0].getAttribute('aria-checked')).toBe('true');
    press('ArrowUp');
    expect(radios()[9].getAttribute('aria-checked')).toBe('true');
  });

  it('returns focus to the opener when destroyed', async () => {
    vi.useFakeTimers();
    try {
      const opener = document.createElement('button');
      document.body.appendChild(opener);
      opener.focus();
      await setup();
      fixture.destroy();
      vi.runAllTimers();
      expect(document.activeElement).toBe(opener);
      opener.remove();
    } finally {
      vi.useRealTimers();
    }
  });
});
