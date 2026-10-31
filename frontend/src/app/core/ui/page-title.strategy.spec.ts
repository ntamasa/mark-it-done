import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { PageTitleStrategy } from './page-title.strategy';

@Component({ template: '' })
class Dummy {}

describe('PageTitleStrategy', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'week', title: 'Week', component: Dummy },
          { path: 'none', component: Dummy },
        ]),
        { provide: TitleStrategy, useExisting: PageTitleStrategy },
      ],
    });
  });

  it('formats the title with the app name', async () => {
    await TestBed.inject(Router).navigateByUrl('/week');
    expect(TestBed.inject(Title).getTitle()).toBe('Week · markitDONE');
  });

  it('falls back to the app name', async () => {
    await TestBed.inject(Router).navigateByUrl('/none');
    expect(TestBed.inject(Title).getTitle()).toBe('markitDONE');
  });
});
