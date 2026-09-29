import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults, typeInto } from '../../testing/providers';
import { ForgotPassword } from './forgot-password';
import { ResetPassword } from './reset-password';

describe('Password pages', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForgotPassword, ResetPassword],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  describe('ForgotPassword', () => {
    it('sends the e-mail and shows the same neutral confirmation', async () => {
      const fixture = TestBed.createComponent(ForgotPassword);
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;

      typeInto(el, '#email', 'melba@gmail.com');
      el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
      const req = httpTesting.expectOne('/api/auth/password/forgot');
      expect(req.request.body).toEqual({ email: 'melba@gmail.com' });
      req.flush(null, { status: 202, statusText: 'Accepted' });
      await fixture.whenStable();

      expect(el.querySelector('[role="status"]')?.textContent).toContain('Revisá tu correo');
      expect(el.textContent).toContain('Si melba@gmail.com corresponde a un cliente');
    });
  });

  describe('ResetPassword', () => {
    async function render(token?: string) {
      const fixture = TestBed.createComponent(ResetPassword);
      if (token) fixture.componentRef.setInput('token', token);
      fixture.detectChanges();
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('asks for a new link when the token is missing', async () => {
      const { el } = await render();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain(
        'El enlace está incompleto',
      );
    });

    it('requires matching passwords', async () => {
      const { fixture, el } = await render('abc');
      typeInto(el, '#newPassword', 'a-long-enough-password');
      typeInto(el, '#confirm', 'something-different');
      el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
      await fixture.whenStable();

      expect(el.textContent).toContain('Las contraseñas no coinciden.');
      httpTesting.expectNone('/api/auth/password/reset');
    });

    it('resets the password with the token from the link', async () => {
      const { fixture, el } = await render('abc');
      typeInto(el, '#newPassword', 'a-long-enough-password');
      typeInto(el, '#confirm', 'a-long-enough-password');
      el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

      const req = httpTesting.expectOne('/api/auth/password/reset');
      expect(req.request.body).toEqual({ token: 'abc', newPassword: 'a-long-enough-password' });
      req.flush(null, { status: 204, statusText: 'No Content' });
      await fixture.whenStable();

      expect(el.querySelector('[role="status"]')?.textContent).toContain('Tu contraseña se cambió');
    });

    it('explains expired links and offers a new one', async () => {
      const { fixture, el } = await render('old');
      typeInto(el, '#newPassword', 'a-long-enough-password');
      typeInto(el, '#confirm', 'a-long-enough-password');
      el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
      httpTesting
        .expectOne('/api/auth/password/reset')
        .flush(null, { status: 400, statusText: 'Bad Request' });
      await fixture.whenStable();

      const alert = el.querySelector('[role="alert"]')!;
      expect(alert.textContent).toContain('El enlace venció o ya fue usado.');
      expect(alert.querySelector('a')?.getAttribute('href')).toBe('/forgot-password');
    });
  });
});
