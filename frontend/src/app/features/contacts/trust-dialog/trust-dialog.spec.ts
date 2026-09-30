import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { Contact } from '../../../core/models/contact.model';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { TrustDialog, TrustDialogData } from './trust-dialog';

const URL = '/api/clients/current/contacts/1/trust';
const LUCIA: Contact = {
  id: 1,
  alias: 'Lucía',
  accountNumber: 'VIN-27905812',
  holderDisplay: 'Lucía P.',
  createdAt: '2026-09-01T10:00:00',
  trusted: false,
};

describe('TrustDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  async function render() {
    dialogRef.close.mockReset();
    const data: TrustDialogData = { contact: LUCIA };
    await TestBed.configureTestingModule({
      imports: [TrustDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TrustDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const submit = async () => {
      el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
      await fixture.whenStable();
    };
    return { fixture, el, submit };
  }

  afterEach(() => httpTesting.verify());

  it('sends the code and closes with the trusted recipient', async () => {
    const { el, submit } = await render();
    expect(el.textContent).toContain('Lucía P.');
    typeInto(el, '#trustCode', '123456');
    await submit();
    const req = httpTesting.expectOne(URL);
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({ ...LUCIA, trusted: true });
    expect(dialogRef.close).toHaveBeenCalledWith({ ...LUCIA, trusted: true });
  });

  it('needs six digits before calling the API', async () => {
    const { el, submit } = await render();
    typeInto(el, '#trustCode', '12');
    await submit();
    httpTesting.expectNone(URL);
    expect(el.textContent).toContain('Ingresá los 6 dígitos del código.');
  });

  it('explains a wrong code and stays open', async () => {
    const { fixture, el, submit } = await render();
    typeInto(el, '#trustCode', '111111');
    await submit();
    httpTesting
      .expectOne(URL)
      .flush({ secondFactor: 'INVALID' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('El código no es correcto');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('sends to the profile when two-step verification is off', async () => {
    const { fixture, el, submit } = await render();
    typeInto(el, '#trustCode', '111111');
    await submit();
    httpTesting
      .expectOne(URL)
      .flush({ code: 'TWO_FACTOR_REQUIRED' }, { status: 422, statusText: 'Unprocessable Entity' });
    await fixture.whenStable();
    expect(el.querySelector('a[href="/profile"]')).not.toBeNull();
    expect(el.querySelector('#trustCode')).toBeNull();
  });
});
