import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Client } from '../../../core/models/client.model';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { TwoFactorCard } from './two-factor-card';

const URL = '/api/clients/current/2fa';

describe('TwoFactorCard', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TwoFactorCard],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(enabled: boolean) {
    const fixture = TestBed.createComponent(TwoFactorCard);
    fixture.componentRef.setInput('enabled', enabled);
    const changed: Client[] = [];
    fixture.componentInstance.changed.subscribe((c) => changed.push(c));
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement, changed };
  }

  async function click(fixture: ComponentFixture<TwoFactorCard>, text: string) {
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes(text))!
      .click();
    await fixture.whenStable();
  }

  it('enrolls: shows the secret to scan, then confirms a code', async () => {
    const { fixture, el, changed } = await render(false);
    expect(el.textContent).toContain('Desactivada');

    await click(fixture, 'Activar');
    httpTesting.expectOne(`${URL}/setup`).flush({
      secret: 'JBSWY3DPEHPK3PXP',
      otpauthUri: 'otpauth://totp/MindHub:melba?secret=JBSWY3DPEHPK3PXP',
    });
    await fixture.whenStable();
    expect(el.querySelector('.secret')?.textContent).toBe('JBSW Y3DP EHPK 3PXP');

    typeInto(el, '#enrollCode', '123456');
    await click(fixture, 'Confirmar y activar');
    const req = httpTesting.expectOne(`${URL}/enable`);
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({ id: 1, twoFactorEnabled: true });
    await fixture.whenStable();

    expect(changed).toEqual([{ id: 1, twoFactorEnabled: true }]);
    expect(el.querySelector('.secret')).toBeNull();
  });

  it('explains a wrong code and lets the client try again', async () => {
    const { fixture, el } = await render(false);
    await click(fixture, 'Activar');
    httpTesting.expectOne(`${URL}/setup`).flush({ secret: 'ABCD', otpauthUri: 'otpauth://x' });
    await fixture.whenStable();

    typeInto(el, '#enrollCode', '000000');
    await click(fixture, 'Confirmar y activar');
    httpTesting
      .expectOne(`${URL}/enable`)
      .flush({ secondFactor: 'INVALID' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('El código no es correcto');
    expect(el.querySelector<HTMLInputElement>('#enrollCode')!.value).toBe('');
  });

  it('disables with password and code', async () => {
    const { fixture, el, changed } = await render(true);
    expect(el.textContent).toContain('Activada');

    await click(fixture, 'Desactivar');
    typeInto(el, '#disablePassword', 'wrong');
    typeInto(el, '#disableCode', '123456');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    httpTesting
      .expectOne(`${URL}/disable`)
      .flush(null, { status: 422, statusText: 'Unprocessable Entity' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'La contraseña no es correcta',
    );

    typeInto(el, '#disablePassword', 'secret');
    typeInto(el, '#disableCode', '654321');
    el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    const req = httpTesting.expectOne(`${URL}/disable`);
    expect(req.request.body).toEqual({ password: 'secret', code: '654321' });
    req.flush({ id: 1, twoFactorEnabled: false });
    await fixture.whenStable();
    expect(changed).toEqual([{ id: 1, twoFactorEnabled: false }]);
  });
});
