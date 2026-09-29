import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { Card } from '../../../core/models/card.model';
import { provideTestDefaults } from '../../../testing/providers';
import { RequestCardDialog, RequestCardDialogData } from './request-card-dialog';

const GOLD_DEBIT: Card = {
  id: 1,
  cardholder: 'Melba Morel',
  type: 'DEBIT',
  color: 'GOLD',
  last4: '1111',
  fromDate: '2026-03-01',
  thruDate: '2031-03-01',
  expired: false,
};

describe('RequestCardDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  beforeEach(async () => {
    dialogRef.close.mockReset();
    const data: RequestCardDialogData = { existing: [GOLD_DEBIT], cardholder: 'Melba Morel' };
    await TestBed.configureTestingModule({
      imports: [RequestCardDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function render() {
    const fixture = TestBed.createComponent(RequestCardDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function swatch(el: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll<HTMLButtonElement>('.swatch')).find((b) =>
      b.textContent?.includes(label),
    )!;
  }

  it('marks combinations the client already has and blocks them', async () => {
    const { fixture, el } = await render();
    swatch(el, 'Gold').click();
    await fixture.whenStable();

    expect(swatch(el, 'Gold').textContent).toContain('Ya la tenés');
    expect(el.querySelector<HTMLButtonElement>('button.submit')!.disabled).toBe(true);
  });

  it('issues the card and reveals the full number and CVV once', async () => {
    const { fixture, el } = await render();
    swatch(el, 'Titanium').click();
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('button.submit')!.click();

    const req = httpTesting.expectOne('/api/clients/current/cards');
    expect(req.request.body).toEqual({ type: 'DEBIT', color: 'TITANIUM' });
    req.flush({
      card: { ...GOLD_DEBIT, id: 2, color: 'TITANIUM', last4: '7890' },
      number: '4507991234567890',
      cvv: '321',
    });
    await fixture.whenStable();

    expect(el.textContent).toContain('¡Tu tarjeta está lista!');
    expect(el.querySelector('.number')?.textContent).toBe('4507 9912 3456 7890');
    expect(el.textContent).toContain('321');
    expect(el.textContent).toContain('Es la única vez');

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Listo'))!
      .click();
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('shows the conflict message on 409', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('button.submit')!.click();
    httpTesting
      .expectOne('/api/clients/current/cards')
      .flush(null, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Ya tenés una tarjeta activa',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
