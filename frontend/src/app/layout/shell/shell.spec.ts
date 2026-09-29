import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/auth.model';
import { provideTestDefaults } from '../../testing/providers';
import { Shell } from './shell';

describe('Shell', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function renderAs(role: Role) {
    TestBed.inject(AuthService).login({ email: 'x@test.com', password: 'x' }).subscribe();
    httpTesting
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, role });

    const fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    httpTesting.expectOne('/api/clients/current').flush({
      id: 1,
      name: 'Melba',
      lastName: 'Morel',
      email: 'melba@gmail.com',
      role,
      accounts: [],
    });
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it("shows the user's initials and name", async () => {
    const { el } = await renderAs('CLIENT');
    expect(el.querySelector('.avatar')?.textContent?.trim()).toBe('MM');
    expect(el.querySelector('.user-name')?.textContent).toContain('Melba');
  });

  it('shows "Mis cuentas" to clients only', async () => {
    const { el } = await renderAs('CLIENT');
    const links = Array.from(el.querySelectorAll('.nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual([expect.stringContaining('Mis cuentas')]);
  });

  it('shows "Clientes" to admins only', async () => {
    const { el } = await renderAs('ADMIN');
    const links = Array.from(el.querySelectorAll('.nav a')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/manager']);
  });
});
