import { TestBed } from '@angular/core/testing';
import { Category } from '../../../core/categories/category.models';
import { CategoryChip } from './category-chip';

describe('CategoryChip', () => {
  function render(category: Category | null, size?: 'sm' | 'md') {
    const fixture = TestBed.createComponent(CategoryChip);
    fixture.componentRef.setInput('category', category);
    if (size) {
      fixture.componentRef.setInput('size', size);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the name with the swatch colours', () => {
    const el = render({ id: '1', name: 'Work', swatch: 'INDIGO', itemCount: 0 });
    const chip = el.querySelector<HTMLElement>('.chip')!;
    expect(chip.textContent?.trim()).toBe('Work');
    expect(chip.style.backgroundColor).toBe('rgb(199, 210, 254)');
    expect(chip.style.color).toBe('rgb(49, 46, 129)');
    expect(chip.classList.contains('chip-sm')).toBe(false);
  });

  it('renders the small size', () => {
    const el = render({ id: '1', name: 'Work', swatch: 'TEAL', itemCount: 0 }, 'sm');
    expect(el.querySelector('.chip-sm')).not.toBeNull();
  });

  it('renders nothing for null', () => {
    expect(render(null).querySelector('.chip')).toBeNull();
  });
});
