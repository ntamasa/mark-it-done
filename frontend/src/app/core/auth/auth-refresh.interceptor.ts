import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const NO_REFRESH_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/csrf',
  '/api/auth/me',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
];

export const authRefreshInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const skip = NO_REFRESH_PATHS.some((p) => req.url.includes(p));

  return next(req).pipe(
    catchError((err: unknown) => {
      if (skip || !(err instanceof HttpErrorResponse) || err.status !== 401) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        catchError((refreshErr: unknown) => {
          auth.clearAndRedirect(router.url);
          return throwError(() => refreshErr);
        }),
        switchMap(() => next(req)),
      );
    }),
  );
};
