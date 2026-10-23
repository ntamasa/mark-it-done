import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Adds credentials (cookies) to `/api` requests only.
 * Allows the server to set and receive HttpOnly cookies for authentication and CSRF protection.
 */
@Injectable()
export class CredentialsInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Only add credentials for /api requests
    if (req.url.includes('/api')) {
      req = req.clone({ withCredentials: true });
    }
    return next.handle(req);
  }
}
