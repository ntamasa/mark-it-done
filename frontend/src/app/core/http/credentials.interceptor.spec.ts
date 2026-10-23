import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { CredentialsInterceptor } from './credentials.interceptor';

describe('CredentialsInterceptor', () => {
  let httpMock: HttpTestingController;
  let httpClient: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        {
          provide: HTTP_INTERCEPTORS,
          useClass: CredentialsInterceptor,
          multi: true,
        },
      ],
    });

    httpMock = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds credentials only for /api URLs', () => {
    // Make a request to /api
    httpClient.get('/api/todos').subscribe();
    const apiRequest = httpMock.expectOne('/api/todos');
    expect(apiRequest.request.withCredentials).toBe(true);

    // Make a request to a non-/api URL
    httpClient.get('/other').subscribe();
    const otherRequest = httpMock.expectOne('/other');
    expect(otherRequest.request.withCredentials).toBe(false);
  });

  it('adds credentials to /api requests within a larger URL', () => {
    httpClient.get('/api/users/123').subscribe();
    const request = httpMock.expectOne('/api/users/123');
    expect(request.request.withCredentials).toBe(true);
  });
});
