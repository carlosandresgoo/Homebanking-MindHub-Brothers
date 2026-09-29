import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { TokenResponse } from '../models/auth.model';
import { AuthService } from './auth.service';

const TOKEN: TokenResponse = {
  accessToken: 'jwt-1',
  tokenType: 'Bearer',
  expiresIn: 900,
  role: 'CLIENT',
};

describe('AuthService', () => {
  let auth: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('starts without a session', () => {
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });

  it('login() stores the access token in memory only', () => {
    let role: string | undefined;
    auth.login({ email: 'melba@gmail.com', password: 'secret' }).subscribe((r) => (role = r));

    const req = httpTesting.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush(TOKEN);

    expect(role).toBe('CLIENT');
    expect(auth.accessToken()).toBe('jwt-1');
    expect(auth.isAuthenticated()).toBe(true);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('refresh() shares a single in-flight request between callers', () => {
    const results: string[] = [];
    auth.refresh().subscribe((t) => results.push(t));
    auth.refresh().subscribe((t) => results.push(t));

    httpTesting.expectOne('/api/auth/refresh').flush({ ...TOKEN, accessToken: 'jwt-2' });

    expect(results).toEqual(['jwt-2', 'jwt-2']);
    expect(auth.accessToken()).toBe('jwt-2');
  });

  it('restoreSession() resolves even when there is no refresh cookie', async () => {
    const restored = auth.restoreSession();
    httpTesting
      .expectOne('/api/auth/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await expect(restored).resolves.toBeUndefined();
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('logout() revokes the refresh token server-side and clears the session', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    auth.login({ email: 'melba@gmail.com', password: 'secret' }).subscribe();
    httpTesting.expectOne('/api/auth/login').flush(TOKEN);

    auth.logout().subscribe();
    httpTesting
      .expectOne('/api/auth/logout')
      .flush(null, { status: 204, statusText: 'No Content' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/');
  });
});
