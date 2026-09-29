import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Login } from './login';

describe('Login', () => {
  let httpTesting: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => httpTesting.verify());

  function render(returnUrl?: string) {
    const fixture = TestBed.createComponent(Login);
    if (returnUrl) fixture.componentRef.setInput('returnUrl', returnUrl);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const type = (selector: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const submit = () => el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    return { fixture, el, type, submit };
  }

  const ok = (role: 'CLIENT' | 'ADMIN') => ({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, role });

  it('does not call the API when the form is invalid', async () => {
    const { fixture, el, submit } = render();
    submit();
    await fixture.whenStable();
    expect(el.querySelector('#email')!.classList).toContain('is-invalid');
    httpTesting.expectNone('/api/auth/login');
  });

  it('sends clients to /accounts and admins to /manager', () => {
    const { type, submit } = render();
    type('#email', 'melba@gmail.com');
    type('#password', 'secret');
    submit();
    httpTesting.expectOne('/api/auth/login').flush(ok('ADMIN'));
    expect(navigate).toHaveBeenCalledWith('/manager');
  });

  it('honours an internal returnUrl but ignores external ones', () => {
    const internal = render('/accounts');
    internal.type('#email', 'melba@gmail.com');
    internal.type('#password', 'secret');
    internal.submit();
    httpTesting.expectOne('/api/auth/login').flush(ok('CLIENT'));
    expect(navigate).toHaveBeenLastCalledWith('/accounts');

    const external = render('//evil.example/phish');
    external.type('#email', 'melba@gmail.com');
    external.type('#password', 'secret');
    external.submit();
    httpTesting.expectOne('/api/auth/login').flush(ok('ADMIN'));
    expect(navigate).toHaveBeenLastCalledWith('/manager');
  });

  it('shows a generic message and clears the password on 401', async () => {
    const { fixture, el, type, submit } = render();
    type('#email', 'melba@gmail.com');
    type('#password', 'wrong');
    submit();
    httpTesting.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Invalid email or password');
    expect(el.querySelector<HTMLInputElement>('#password')!.value).toBe('');
  });

  it('explains rate limiting on 429', async () => {
    const { fixture, el, type, submit } = render();
    type('#email', 'melba@gmail.com');
    type('#password', 'wrong');
    submit();
    httpTesting.expectOne('/api/auth/login').flush(null, { status: 429, statusText: 'Too Many Requests' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Too many attempts');
  });
});
