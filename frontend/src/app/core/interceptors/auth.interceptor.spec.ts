import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { TokenResponse } from '../models/auth.model';
import { authInterceptor } from './auth.interceptor';

const token = (accessToken: string): TokenResponse => ({
  accessToken,
  tokenType: 'Bearer',
  expiresIn: 900,
  role: 'CLIENT',
});

describe('authInterceptor', () => {
  let http: HttpClient;
  let auth: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    auth = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  function logIn(accessToken: string): void {
    auth.login({ email: 'melba@gmail.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/auth/login').flush(token(accessToken));
  }

  it('adds the Bearer token to API requests', () => {
    logIn('jwt-1');
    http.get('/api/clients/current').subscribe();
    const req = httpTesting.expectOne('/api/clients/current');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    req.flush({});
  });

  it('never sends the token to other hosts', () => {
    logIn('jwt-1');
    http.get('https://example.com/api/clients').subscribe();
    const req = httpTesting.expectOne('https://example.com/api/clients');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('refreshes once on 401 and retries with the new token', () => {
    logIn('expired');
    let body: unknown;
    http.get('/api/clients/current').subscribe((b) => (body = b));

    httpTesting.expectOne('/api/clients/current').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpTesting.expectOne('/api/auth/refresh').flush(token('fresh'));
    const retry = httpTesting.expectOne('/api/clients/current');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer fresh');
    retry.flush({ ok: true });

    expect(body).toEqual({ ok: true });
  });

  it('expires the session when the refresh also fails', () => {
    logIn('expired');
    const expire = vi.spyOn(auth, 'expireSession').mockImplementation(() => undefined);
    let failedWith: number | undefined;
    http.get('/api/clients/current').subscribe({ error: (e: { status: number }) => (failedWith = e.status) });

    httpTesting.expectOne('/api/clients/current').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpTesting.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(expire).toHaveBeenCalled();
    expect(failedWith).toBe(401);
  });

  it('does not try to refresh on 403', () => {
    logIn('jwt-1');
    let failedWith: number | undefined;
    http.get('/api/clients').subscribe({ error: (e: { status: number }) => (failedWith = e.status) });

    httpTesting.expectOne('/api/clients').flush(null, { status: 403, statusText: 'Forbidden' });

    httpTesting.expectNone('/api/auth/refresh');
    expect(failedWith).toBe(403);
  });
});
