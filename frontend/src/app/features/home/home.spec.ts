import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../core/auth/auth.service';
import { provideTestDefaults } from '../../testing/providers';
import { Home } from './home';

describe('Home', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: provideTestDefaults(),
    }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the hero, features and about sections', () => {
    const el = render();
    expect(el.querySelector('h1')?.textContent).toContain('La banca que te acompaña');
    expect(el.querySelectorAll('.feature')).toHaveLength(3);
    expect(el.querySelector('#nosotros')?.textContent).toContain('Sobre nosotros');
  });

  it('sends anonymous visitors to the login page', () => {
    const el = render();
    const cta = el.querySelector('.hero-actions a');
    expect(cta?.getAttribute('href')).toBe('/login');
    expect(cta?.textContent).toContain('Ingresar a mi cuenta');
  });

  it('sends logged-in clients straight to their accounts', async () => {
    TestBed.inject(AuthService).login({ email: 'melba@gmail.com', password: 'x' }).subscribe();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, role: 'CLIENT' });

    const el = render();
    const cta = el.querySelector('.hero-actions a');
    expect(cta?.getAttribute('href')).toBe('/accounts');
    expect(cta?.textContent).toContain('Ir a mi banca');
  });
});
