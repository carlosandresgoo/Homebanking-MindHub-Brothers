import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { Role } from '../models/auth.model';
import { authGuard, roleGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('auth guards', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  });

  function logInAs(role: Role): void {
    TestBed.inject(AuthService).login({ email: 'x@test.com', password: 'secret' }).subscribe();
    httpTesting
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, role });
  }

  const route = {} as ActivatedRouteSnapshot;
  const state = { url: '/accounts' } as RouterStateSnapshot;

  it('authGuard redirects anonymous users to /login with a returnUrl', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(route, state)) as UrlTree;
    expect(result.toString()).toBe('/login?returnUrl=%2Faccounts');
  });

  it('authGuard lets logged-in users through', () => {
    logInAs('CLIENT');
    expect(TestBed.runInInjectionContext(() => authGuard(route, state))).toBe(true);
  });

  it('roleGuard sends users without the role back home', () => {
    logInAs('CLIENT');
    const result = TestBed.runInInjectionContext(() => roleGuard('ADMIN')(route, state)) as UrlTree;
    expect(result.toString()).toBe('/');
  });

  it('roleGuard allows the matching role', () => {
    logInAs('ADMIN');
    expect(TestBed.runInInjectionContext(() => roleGuard('ADMIN')(route, state))).toBe(true);
  });
});
