import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, HTTP_INTERCEPTORS, withXsrfConfiguration } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { MessageService, ConfirmationService } from 'primeng/api';
import { routes } from './app.routes';
import { MarkitdonePreset } from './core/theme/markitdone-preset';
import { CredentialsInterceptor } from './core/http/credentials.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
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
