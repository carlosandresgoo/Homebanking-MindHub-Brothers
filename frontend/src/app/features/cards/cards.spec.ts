import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Card } from '../../core/models/card.model';
import { provideTestDefaults } from '../../testing/providers';
import { Cards } from './cards';

const MELBA = {
  id: 1,
  name: 'Melba',
  lastName: 'Morel',
  email: 'melba@gmail.com',
  role: 'CLIENT',
  accounts: [],
};
const CARDS: Card[] = [
  {
    id: 1,
    cardholder: 'Melba Morel',
    type: 'DEBIT',
    color: 'GOLD',
    last4: '1111',
    fromDate: '2026-03-01',
    thruDate: '2031-03-01',
    expired: false,
  },
  {
    id: 2,
    cardholder: 'Melba Morel',
    type: 'CREDIT',
    color: 'TITANIUM',
    last4: '2222',
    fromDate: '2026-07-01',
    thruDate: '2031-07-01',
    expired: false,
  },
];

describe('Cards', () => {
  let httpTesting: HttpTestingController;
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    dialog.open.mockReset();
    await TestBed.configureTestingModule({
      imports: [Cards],
      providers: [...provideTestDefaults(), { provide: MatDialog, useValue: dialog }],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render(cards: Card[]) {
    const fixture = TestBed.createComponent(Cards);
    fixture.detectChanges();
    httpTesting.expectOne('/api/clients/current/cards').flush(cards);
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('groups cards by type, debit first, masked', async () => {
    const { el } = await render(CARDS);
    const groups = Array.from(el.querySelectorAll('.group h2')).map((h) => h.textContent?.trim());
    expect(groups).toEqual(['Débito', 'Crédito']);
    expect(el.querySelectorAll('app-bank-card')).toHaveLength(2);
    expect(el.textContent).toContain('•••• •••• •••• 1111');
  });

  it('shows an empty state inviting to request a card', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('Todavía no tenés tarjetas');
  });

  it('opens the request dialog with existing cards and reloads after issuing', async () => {
    const { fixture, el } = await render(CARDS);
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    el.querySelector<HTMLButtonElement>('.request-button')!.click();

    const config = dialog.open.mock.calls[0][1];
    expect(config.data.existing).toHaveLength(2);
    expect(config.data.cardholder).toBe('Melba Morel');
    httpTesting.expectOne('/api/clients/current/cards').flush(CARDS);
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
    await fixture.whenStable();
  });

  it('deactivates a card after confirmation', async () => {
    const { el } = await render(CARDS);
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    el.querySelector<HTMLButtonElement>(
      'button[aria-label="Desactivar la tarjeta terminada en 1111"]',
    )!.click();

    const req = httpTesting.expectOne('/api/cards/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    httpTesting.expectOne('/api/clients/current/cards').flush([CARDS[1]]);
    httpTesting.expectOne('/api/clients/current').flush(MELBA);
  });

  it('does nothing when the deactivation is cancelled', async () => {
    const { el } = await render(CARDS);
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });

    el.querySelector<HTMLButtonElement>(
      'button[aria-label="Desactivar la tarjeta terminada en 1111"]',
    )!.click();

    httpTesting.expectNone('/api/cards/1');
  });
});
