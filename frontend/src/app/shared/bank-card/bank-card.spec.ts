import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { BankCard } from './bank-card';

describe('BankCard', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BankCard],
      providers: provideTestDefaults(),
    }).compileComponents();
  });

  function render(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(BankCard);
    fixture.componentRef.setInput('type', 'CREDIT');
    fixture.componentRef.setInput('color', 'GOLD');
    fixture.componentRef.setInput('cardholder', 'Melba Morel');
    fixture.componentRef.setInput('thruDate', '2031-09-29');
    for (const [key, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(key, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a masked number, holder, expiry and finish', () => {
    const el = render({ last4: '4321' });
    const card = el.querySelector('.card')!;
    expect(card.classList).toContain('gold');
    expect(el.querySelector('.number')?.textContent).toBe('•••• •••• •••• 4321');
    expect(el.textContent).toContain('MELBA MOREL');
    expect(el.textContent).toContain('09/31');
    expect(card.getAttribute('aria-label')).toBe('Tarjeta de crédito Gold terminada en 4321');
  });

  it('shows the full number grouped and the CVV right after issuing', () => {
    const el = render({ fullNumber: '4507991234567890', cvv: '123' });
    expect(el.querySelector('.number')?.textContent).toBe('4507 9912 3456 7890');
    expect(el.textContent).toContain('CVV');
    expect(el.textContent).toContain('123');
  });

  it('flags expired cards', () => {
    const el = render({ last4: '4321', expired: true });
    expect(el.querySelector('.expired')?.textContent).toContain('Vencida');
  });
});
