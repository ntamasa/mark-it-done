import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { Category } from '../../core/categories/category.models';
import { CategoryStore } from '../../core/categories/category.store';
import { CategoriesPage } from './categories';

describe('CategoriesPage', () => {
  const cats: Category[] = [
    { id: 'a', name: 'Home', swatch: 'PINK', itemCount: 0 },
    { id: 'b', name: 'Work', swatch: 'TEAL', itemCount: 1 },
    { id: 'c', name: 'Gym', swatch: 'LIME', itemCount: 3 },
  ];
  const requestDelete = vi.fn();

  function setup() {
    TestBed.configureTestingModule({
      providers: [MessageService, { provide: CategoryStore, useValue: { categories: signal(cats), requestDelete } }],
    });
    const fixture = TestBed.createComponent(CategoriesPage);
    fixture.detectChanges();
    return fixture;
  }

  it('shows the count texts', () => {
    const el = setup().nativeElement as HTMLElement;
    const counts = Array.from(el.querySelectorAll('.cat-count')).map((e) => e.textContent?.trim());
    expect(counts).toEqual(['No todos', '1 item', '3 items']);
  });

  it('labels the action buttons with the category name', () => {
    const el = setup().nativeElement as HTMLElement;
    expect(el.querySelector('button[aria-label="Edit category Work"]')).not.toBeNull();
    expect(el.querySelector('button[aria-label="Delete category Work"]')).not.toBeNull();
  });

  it('delete calls requestDelete', () => {
    const el = setup().nativeElement as HTMLElement;
    el.querySelector<HTMLElement>('button[aria-label="Delete category Work"]')!.click();
    expect(requestDelete).toHaveBeenCalledWith(cats[1]);
  });

  it('opens the dialog for new and edit', () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLElement>('button[aria-label="New category"]')!.click();
    fixture.detectChanges();
    expect(el.querySelector('#catedit-title')?.textContent).toBe('New category');
  });

  it('opens the edit dialog prefilled', async () => {
    const fixture = setup();
    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLElement>('button[aria-label="Edit category Work"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el.querySelector('#catedit-title')?.textContent).toBe('Edit category');
    expect(el.querySelector<HTMLInputElement>('#cat-name')!.value).toBe('Work');
  });
});
