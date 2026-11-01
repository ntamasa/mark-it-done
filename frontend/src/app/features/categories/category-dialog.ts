import { BreakpointObserver } from '@angular/cdk/layout';
import { DOCUMENT } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import {
  ChangeDetectionStrategy,
  ElementRef,
  OnDestroy,
  Component,
  computed,
  inject,
  input,
  OnInit,
  output,
  signal,
} from '@angular/core';
import { FieldTree, FormField, form, maxLength, required, submit, validate } from '@angular/forms/signals';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { firstValueFrom } from 'rxjs';
import { Category, SWATCH_KEYS, SWATCHES, Swatch } from '../../core/categories/category.models';
import { CategoryStore } from '../../core/categories/category.store';
import { toApiProblem } from '../../core/http/api-error';
import { NotifyService } from '../../core/ui/notify.service';
import { CategoryChip } from '../../shared/ui/category-chip/category-chip';

export const NAME_REQUIRED_MESSAGE = 'Name is required.';
export const NAME_TAKEN_MESSAGE = 'You already have a category with this name.';

interface CategoryFormModel {
  name: string;
  swatch: Swatch;
}

@Component({
  selector: 'app-category-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DialogModule, ButtonModule, FormField, CategoryChip],
  templateUrl: './category-dialog.html',
  styleUrl: './category-dialog.scss',
})
export class CategoryDialog implements OnInit, OnDestroy {
  private readonly store = inject(CategoryStore);
  private readonly notify = inject(NotifyService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly doc = inject(DOCUMENT);
  private opener: HTMLElement | null = null;

  protected readonly isMobile = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 767.98px)')
      .pipe(map((s) => s.matches)),
    { initialValue: false },
  );

  /** The category to edit; null creates a new one. */
  readonly category = input<Category | null>(null);
  readonly closed = output<void>();

  protected readonly swatches = SWATCH_KEYS.map((key) => ({ key, ...SWATCHES[key] }));
  protected readonly title = computed(() => (this.category() ? 'Edit category' : 'New category'));

  private readonly model = signal<CategoryFormModel>({ name: '', swatch: 'TEAL' });

  protected readonly categoryForm = form(this.model, (p) => {
    required(p.name, { message: NAME_REQUIRED_MESSAGE });
    validate(p.name, ({ value }) =>
      value().trim() === '' ? { kind: 'required', message: NAME_REQUIRED_MESSAGE } : undefined,
    );
    maxLength(p.name, 40, { message: 'Name must be at most 40 characters.' });
    required(p.swatch);
  });

  protected readonly saving = signal(false);
  protected readonly conflict = signal<string | null>(null);
  private readonly submitAttempted = signal(false);

  protected readonly preview = computed<Category>(() => ({
    id: 'preview',
    name: this.model().name.trim() || 'Preview',
    swatch: this.model().swatch,
    itemCount: 0,
  }));

  protected readonly nameError = computed(() => {
    const conflict = this.conflict();
    if (conflict) {
      return conflict;
    }
    return this.errorOf(this.categoryForm.name);
  });

  ngOnInit(): void {
    this.opener = this.doc.activeElement instanceof HTMLElement ? this.doc.activeElement : null;
    const c = this.category();
    if (c) {
      this.model.set({ name: c.name, swatch: c.swatch });
    }
  }

  /** The first error message of a field, shown after blur or a submit attempt. */
  private errorOf(field: FieldTree<string>): string | null {
    const state = field();
    if (!(state.touched() || this.submitAttempted())) {
      return null;
    }
    return state.errors()[0]?.message ?? null;
  }

  ngOnDestroy(): void {
    const opener = this.opener;
    // Let PrimeNG finish its own focus handling first, then hand focus back to the opener.
    setTimeout(() => opener?.isConnected && opener.focus());
  }

  /** PrimeNG labels the dialog with its (hidden) header; point it at our own title instead. */
  protected onShow(): void {
    this.host.nativeElement.querySelector('[role="dialog"]')?.setAttribute('aria-labelledby', 'catedit-title');
  }

  /** Arrow keys move focus and selection through the swatches, wrapping around. */
  protected onSwatchKey(event: KeyboardEvent): void {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[event.key];
    if (step === undefined) {
      return;
    }
    event.preventDefault();
    const index = SWATCH_KEYS.indexOf(this.model().swatch);
    const next = (index + step + SWATCH_KEYS.length) % SWATCH_KEYS.length;
    this.pick(SWATCH_KEYS[next]);
    const buttons = this.host.nativeElement.querySelectorAll<HTMLElement>('[role="radio"]');
    buttons[next]?.focus();
  }

  protected pick(swatch: Swatch): void {
    this.model.update((m) => ({ ...m, swatch }));
  }

  protected onVisibleChange(visible: boolean): void {
    if (!visible) {
      this.closed.emit();
    }
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) {
      return;
    }
    this.submitAttempted.set(true);
    this.conflict.set(null);
    await submit(this.categoryForm, {
      action: async () => {
        await this.save();
      },
      onInvalid: () => this.categoryForm.name().focusBoundControl(),
    });
  }

  private async save(): Promise<void> {
    const existing = this.category();
    const req = { name: this.model().name.trim(), swatch: this.model().swatch };
    this.saving.set(true);
    try {
      await firstValueFrom(existing ? this.store.update(existing.id, req) : this.store.create(req));
      this.notify.success(existing ? 'Category updated' : 'Category created');
      this.closed.emit();
    } catch (err) {
      const problem = toApiProblem(err);
      if (problem.status === 409 || problem.code === 'CATEGORY_NAME_TAKEN') {
        this.conflict.set(NAME_TAKEN_MESSAGE);
        this.categoryForm.name().focusBoundControl();
      } else {
        this.notify.error(problem);
      }
    } finally {
      this.saving.set(false);
    }
  }
}
