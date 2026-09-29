import { TestBed } from '@angular/core/testing';

import { Home } from './home';

describe('Home', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Home] }).compileComponents();
  });

  it('renders the logo and the about-us text', () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('img.logo')?.getAttribute('src')).toBe('assets/logo.png');
    expect(el.textContent).toContain('ABOUT US');
  });

  it('toggles the collapsed navbar', async () => {
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const menu = el.querySelector('#navbarSupportedContent')!;
    expect(menu.classList).not.toContain('show');

    el.querySelector<HTMLButtonElement>('.navbar-toggler')!.click();
    await fixture.whenStable();

    expect(menu.classList).toContain('show');
  });
});
