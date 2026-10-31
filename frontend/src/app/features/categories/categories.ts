import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ShellHeader } from '../shell/shell-header';

@Component({
  selector: 'app-categories',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h1 class="mobile-title">Categories</h1>`,
  styles: `
    .mobile-title {
      margin: 0;
      font-size: 22px;
      font-weight: 700;
    }
    @media (min-width: 1024px) {
      .mobile-title {
        display: none;
      }
    }
  `,
})
export class CategoriesPage implements OnInit, OnDestroy {
  private readonly header = inject(ShellHeader);

  ngOnInit(): void {
    this.header.title.set('Categories');
  }

  ngOnDestroy(): void {
    this.header.title.set('');
    this.header.pager.set(null);
    this.header.focusedDate.set(null);
  }
}
