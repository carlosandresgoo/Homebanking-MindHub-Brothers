import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';

import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { NewClientDialog } from './new-client-dialog';

describe('NewClientDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  beforeEach(async () => {
    dialogRef.close.mockReset();
    await TestBed.configureTestingModule({
      imports: [NewClientDialog],
      providers: [...provideTestDefaults(), { provide: MatDialogRef, useValue: dialogRef }],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  function render() {
    const fixture = TestBed.createComponent(NewClientDialog);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const fillValid = () => {
      typeInto(el, '#name', 'Chloe');
      typeInto(el, '#lastName', 'Obrian');
      typeInto(el, '#email', 'chloe@test.com');
      typeInto(el, '#password', 'a-long-enough-password');
    };
    const submit = () => el.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    return { fixture, el, fillValid, submit };
  }

  it('validates before calling the API', async () => {
    const { fixture, el, submit } = render();
    typeInto(el, '#name', 'Chl0e');
    typeInto(el, '#password', 'short');
    submit();
    await fixture.whenStable();

    expect(el.textContent).toContain('Solo letras (con tildes), espacios, apóstrofos o guiones.');
    expect(el.textContent).toContain('Ingresá el apellido.');
    expect(el.textContent).toContain('Entre 12 y 72 caracteres.');
    httpTesting.expectNone('/api/clients');
  });

  it('creates the client and closes with it', () => {
    const { fillValid, submit } = render();
    fillValid();
    submit();

    const req = httpTesting.expectOne({ method: 'POST', url: '/api/clients' });
    expect(req.request.body).toEqual({
      name: 'Chloe',
      lastName: 'Obrian',
      email: 'chloe@test.com',
      password: 'a-long-enough-password',
    });
    const created = {
      id: 3,
      name: 'Chloe',
      lastName: 'Obrian',
      email: 'chloe@test.com',
      role: 'CLIENT',
      accounts: [],
    };
    req.flush(created);

    expect(dialogRef.close).toHaveBeenCalledWith(created);
  });

  it('keeps the dialog open and flags the email on 409', async () => {
    const { fixture, el, fillValid, submit } = render();
    fillValid();
    submit();
    httpTesting
      .expectOne({ method: 'POST', url: '/api/clients' })
      .flush(null, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain(
      'Ya existe un cliente con ese email',
    );
    expect(el.textContent).toContain('Este email ya está registrado.');
  });
});
