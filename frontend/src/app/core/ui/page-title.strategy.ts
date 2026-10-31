import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

export const APP_NAME = 'markitDONE';

/** Sets the document title to "<View> · markitDONE". */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const view = this.buildTitle(snapshot);
    this.title.setTitle(view ? `${view} · ${APP_NAME}` : APP_NAME);
  }
}
