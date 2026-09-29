import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { provideTestDefaults, typeInto } from '../../testing/providers';
import { Register } from './register';

describe('Register', () => {
  let httpTesting: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => httpTesting.verify());

  function render() {
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const fillValid = () => {
      typeInto(el, '#name', 'Chloe');
      typeInto(el, '#lastName', 'Obrian');
      typeInto(el, '#email', 'chloe@test.com');
      typeInto(el, '#password', 'a-long-enough-password');
    };
    const submit = () => el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    return { fixture, el, fillValid, submit };
  }

  it('validates the form before calling the API', async () => {
    const { fixture, el, submit } = render();
    typeInto(el, '#password', 'short');
    submit();
    await fixture.whenStable();

    expect(el.textContent).toContain('Ingresá tu nombre.');
    expect(el.textContent).toContain('Entre 12 y 72 caracteres.');
    httpTesting.expectNone('/api/auth/register');
  });

  it('signs up, starts the session and goes to the accounts page', () => {
    const { fillValid, submit } = render();
    fillValid();
    submit();

    const req = httpTesting.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      name: 'Chloe',
      lastName: 'Obrian',
      email: 'chloe@test.com',
      password: 'a-long-enough-password',
    });
    req.flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, role: 'CLIENT' });

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(true);
    expect(navigate).toHaveBeenCalledWith('/accounts');
  });

  it('flags an email that is already registered', async () => {
    const { fixture, el, fillValid, submit } = render();
    fillValid();
    submit();
    httpTesting
      .expectOne('/api/auth/register')
      .flush(null, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Ya existe una cuenta');
    expect(el.textContent).toContain('Este email ya está registrado.');
    expect(el.querySelector<HTMLInputElement>('#password')!.value).toBe('');
  });
});
