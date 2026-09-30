import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { Contact } from '../../../core/models/contact.model';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { ContactDialog, ContactDialogData } from './contact-dialog';

const URL = '/api/clients/current/contacts';
const LUCIA: Contact = {
  id: 1,
  alias: 'Lucía',
  accountNumber: 'VIN-27905812',
  holderDisplay: 'Lucía P.',
  createdAt: '2026-09-01T10:00:00',
};

describe('ContactDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  async function render(data: ContactDialogData) {
    dialogRef.close.mockReset();
    await TestBed.configureTestingModule({
      imports: [ContactDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ContactDialog);
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const submit = () => el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    return { fixture, el, submit };
  }

  afterEach(() => httpTesting.verify());

  it('adds a recipient by account number, CBU or alias as typed (the API ignores case)', async () => {
    const { el, submit } = await render({ accountNumber: ' sol.rio.mate ' });
    typeInto(el, '#contactAlias', ' Lucía ');
    submit();
    const req = httpTesting.expectOne(URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ accountNumber: 'sol.rio.mate', alias: 'Lucía' });
    req.flush(LUCIA);
    expect(dialogRef.close).toHaveBeenCalledWith(LUCIA);
  });

  it('validates the alias before calling the API', async () => {
    const { fixture, el, submit } = await render({});
    typeInto(el, '#contactAccount', 'VIN-1');
    typeInto(el, '#contactAlias', '<b>');
    submit();
    await fixture.whenStable();
    expect(el.textContent).toContain('Usá letras, números, espacios y . - _ ( )');
    httpTesting.expectNone(URL);
  });

  it('explains an unknown account and a duplicate alias', async () => {
    const { fixture, el, submit } = await render({});
    typeInto(el, '#contactAccount', 'VIN-00000000');
    typeInto(el, '#contactAlias', 'Nadie');
    submit();
    httpTesting.expectOne(URL).flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'No encontramos una cuenta con ese CBU, alias o número',
    );

    submit();
    httpTesting
      .expectOne(URL)
      .flush(
        { detail: 'You already have a recipient with that alias' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Ya tenés un destinatario con ese alias',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('renames an existing recipient without asking for the account again', async () => {
    const { el, submit } = await render({ contact: LUCIA });
    expect(el.querySelector('#contactAccount')).toBeNull();
    expect(el.textContent).toContain('VIN-27905812');

    typeInto(el, '#contactAlias', 'Hermana');
    submit();
    const req = httpTesting.expectOne(`${URL}/1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ alias: 'Hermana' });
    req.flush({ ...LUCIA, alias: 'Hermana' });
    expect(dialogRef.close).toHaveBeenCalledWith({ ...LUCIA, alias: 'Hermana' });
  });
});
