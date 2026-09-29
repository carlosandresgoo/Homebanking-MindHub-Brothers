import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults, typeInto } from '../../testing/providers';
import { Profile } from './profile';

const MELBA = {
  id: 1,
  name: 'Melba',
  lastName: 'Morel',
  email: 'melba@gmail.com',
  role: 'CLIENT',
  accounts: [],
};

describe('Profile', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Profile],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(client: object = MELBA) {
    const fixture = TestBed.createComponent(Profile);
    fixture.detectChanges();
    httpTesting.expectOne('/api/clients/current').flush(client);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const fill = (current: string, next: string, confirm: string) => {
      typeInto(el, '#currentPassword', current);
      typeInto(el, '#newPassword', next);
      typeInto(el, '#confirm', confirm);
    };
    const submit = () => el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    return { fixture, el, fill, submit };
  }

  it('shows the personal data', async () => {
    const { el } = await render();
    expect(el.textContent).toContain('Melba Morel');
    expect(el.textContent).toContain('melba@gmail.com');
    expect(el.querySelector('.avatar')?.textContent?.trim()).toBe('MM');
  });

  it('offers two-step verification to clients only', async () => {
    const client = await render({ ...MELBA, twoFactorEnabled: true });
    expect(client.el.querySelector('app-two-factor-card')?.textContent).toContain('Activada');

    const admin = await render({ ...MELBA, role: 'ADMIN' });
    expect(admin.el.querySelector('app-two-factor-card')).toBeNull();
  });

  it('changes the password and clears the form', async () => {
    const { fixture, el, fill, submit } = await render();
    fill('current-password', 'a-brand-new-password', 'a-brand-new-password');
    submit();

    const req = httpTesting.expectOne('/api/auth/password');
    expect(req.request.body).toEqual({
      currentPassword: 'current-password',
      newPassword: 'a-brand-new-password',
    });
    req.flush({ accessToken: 'jwt-2', tokenType: 'Bearer', expiresIn: 900, role: 'CLIENT' });
    await fixture.whenStable();

    expect(el.querySelector<HTMLInputElement>('#newPassword')!.value).toBe('');
  });

  it('explains a wrong current password', async () => {
    const { fixture, el, fill, submit } = await render();
    fill('wrong', 'a-brand-new-password', 'a-brand-new-password');
    submit();
    httpTesting
      .expectOne('/api/auth/password')
      .flush(
        { detail: 'The current password is incorrect' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'La contraseña actual no es correcta',
    );
  });

  it('does not submit mismatching passwords', async () => {
    const { fixture, el, fill, submit } = await render();
    fill('current-password', 'a-brand-new-password', 'another-new-password');
    submit();
    await fixture.whenStable();

    expect(el.textContent).toContain('Las contraseñas no coinciden.');
    httpTesting.expectNone('/api/auth/password');
  });
});
