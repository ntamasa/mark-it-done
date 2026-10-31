import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, TitleStrategy } from '@angular/router';
import {
  provideHttpClient,
  HTTP_INTERCEPTORS,
  withInterceptors,
  withInterceptorsFromDi,
  withXsrfConfiguration,
} from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { MessageService, ConfirmationService } from 'primeng/api';
import { PageTitleStrategy } from './core/ui/page-title.strategy';
import { routes } from './app.routes';
import { MarkitdonePreset } from './core/theme/markitdone-preset';
import { ThemeService } from './core/theme/theme.service';
import { AuthService } from './core/auth/auth.service';
import { authRefreshInterceptor } from './core/auth/auth-refresh.interceptor';
import { CredentialsInterceptor } from './core/http/credentials.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
    provideHttpClient(
      withInterceptorsFromDi(),
      withInterceptors([authRefreshInterceptor]),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      }),
    ),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: CredentialsInterceptor,
      multi: true,
    },
    provideAppInitializer(() => {
      inject(ThemeService);
      return inject(AuthService).init();
    }),
    MessageService,
    ConfirmationService,
    providePrimeNG({
      theme: {
        preset: MarkitdonePreset,
        options: {
          darkModeSelector: '.app-dark',
          cssLayer: false,
          ripple: false,
        },
      },
    }),
  ],
};
