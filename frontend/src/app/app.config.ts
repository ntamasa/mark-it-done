import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { MarkitdonePreset } from './core/theme/markitdone-preset';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
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
