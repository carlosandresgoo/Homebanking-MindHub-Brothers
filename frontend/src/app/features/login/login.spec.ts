import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { provideTestDefaults, typeInto } from '../../testing/providers';
import { Login } from './login';

describe('Login', () => {
  let httpTesting: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: provideTestDefaults(),
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
    const fill = (email: string, password: string) => {
      typeInto(el, '#email', email);
      typeInto(el, '#password', password);
    };
    const submit = () => el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    return { fixture, el, fill, submit };
  }

  const ok = (role: 'CLIENT' | 'ADMIN') => ({
    accessToken: 'jwt',
    tokenType: 'Bearer',
    expiresIn: 900,
    role,
  });

  it('shows field errors and does not call the API when the form is invalid', async () => {
    const { fixture, el, submit } = render();
    submit();
    await fixture.whenStable();

    expect(el.textContent).toContain('Ingresá tu email.');
    expect(el.textContent).toContain('Ingresá tu contraseña.');
    httpTesting.expectNone('/api/auth/login');
  });

  it('toggles password visibility', async () => {
    const { fixture, el } = render();
    const input = el.querySelector<HTMLInputElement>('#password')!;
    expect(input.type).toBe('password');

    el.querySelector<HTMLButtonElement>('button[aria-label="Mostrar contraseña"]')!.click();
    await fixture.whenStable();

    expect(input.type).toBe('text');
  });

  it('sends clients to /accounts and admins to /manager', () => {
    const client = render();
    client.fill('melba@gmail.com', 'secret');
    client.submit();
    httpTesting.expectOne('/api/auth/login').flush(ok('CLIENT'));
    expect(navigate).toHaveBeenLastCalledWith('/accounts');

    const admin = render();
    admin.fill('admin@mindhub.com', 'secret');
    admin.submit();
    httpTesting.expectOne('/api/auth/login').flush(ok('ADMIN'));
    expect(navigate).toHaveBeenLastCalledWith('/manager');
  });

  it('honours an internal returnUrl but ignores external ones', () => {
    const internal = render('/accounts');
    internal.fill('melba@gmail.com', 'secret');
    internal.submit();
    httpTesting.expectOne('/api/auth/login').flush(ok('ADMIN'));
    expect(navigate).toHaveBeenLastCalledWith('/accounts');

    const external = render('//evil.example/phish');
    external.fill('melba@gmail.com', 'secret');
    external.submit();
    httpTesting.expectOne('/api/auth/login').flush(ok('ADMIN'));
    expect(navigate).toHaveBeenLastCalledWith('/manager');
  });

  it('shows a generic message and clears the password on 401', async () => {
    const { fixture, el, fill, submit } = render();
    fill('melba@gmail.com', 'wrong');
    submit();
    httpTesting
      .expectOne('/api/auth/login')
      .flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'El email o la contraseña no son correctos',
    );
    expect(el.querySelector<HTMLInputElement>('#password')!.value).toBe('');
  });

  it('explains a temporary lock with the time it ends, and an admin block', async () => {
    const { fixture, el, fill, submit } = render();
    fill('melba@gmail.com', 'secret');
    submit();
    httpTesting
      .expectOne('/api/auth/login')
      .flush(
        { reason: 'LOCKED', lockedUntil: '2026-09-29T15:30:00Z' },
        { status: 423, statusText: 'Locked' },
      );
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'bloqueamos tu usuario tras varios intentos fallidos. Probá de nuevo a las',
    );

    fill('melba@gmail.com', 'secret');
    submit();
    httpTesting
      .expectOne('/api/auth/login')
      .flush({ reason: 'BLOCKED' }, { status: 423, statusText: 'Locked' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Tu usuario está bloqueado. Comunicate con el banco',
    );
  });

  it('explains rate limiting on 429', async () => {
    const { fixture, el, fill, submit } = render();
    fill('melba@gmail.com', 'wrong');
    submit();
    httpTesting
      .expectOne('/api/auth/login')
      .flush(null, { status: 429, statusText: 'Too Many Requests' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Demasiados intentos');
  });
});
