import { ChangeDetectionStrategy, Component, inject, OnDestroy, OnInit } from '@angular/core';
import { ShellHeader } from '../shell/shell-header';

@Component({
  selector: 'app-week',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<h1 class="mobile-title">Week</h1>`,
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
export class WeekPage implements OnInit, OnDestroy {
  private readonly header = inject(ShellHeader);

  ngOnInit(): void {
    this.header.title.set('Week');
  }

  ngOnDestroy(): void {
    this.header.title.set('');
    this.header.pager.set(null);
    this.header.focusedDate.set(null);
  }
}
