import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { Account } from '../../../core/models/account.model';
import { provideTestDefaults, typeInto } from '../../../testing/providers';
import { AliasDialog, AliasDialogData } from './alias-dialog';

const URL = '/api/accounts/7/alias';
const ACCOUNT: Account = {
  id: 7,
  number: 'VIN001',
  cbu: '9990001800000000000017',
  alias: 'melba.ahorros',
  creationDate: '2026-08-30T10:00:00',
  balance: 5000,
};

describe('AliasDialog', () => {
  let httpTesting: HttpTestingController;
  const dialogRef = { close: vi.fn(), disableClose: false };

  async function render() {
    dialogRef.close.mockReset();
    const data: AliasDialogData = { account: ACCOUNT };
    await TestBed.configureTestingModule({
      imports: [AliasDialog],
      providers: [
        ...provideTestDefaults(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AliasDialog);
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

  it('starts with the current alias and saves the new one in lower case', async () => {
    const { el, submit } = await render();
    expect(el.querySelector<HTMLInputElement>('#accountAlias')!.value).toBe('melba.ahorros');

    typeInto(el, '#accountAlias', ' Sol.Rio.Mate ');
    await submit();
    const req = httpTesting.expectOne(URL);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ alias: 'sol.rio.mate' });
    req.flush({ ...ACCOUNT, alias: 'sol.rio.mate' });
    expect(dialogRef.close).toHaveBeenCalledWith({ ...ACCOUNT, alias: 'sol.rio.mate' });
  });

  it('rejects aliases that break the rules without calling the API', async () => {
    const { el, submit } = await render();
    for (const invalid of ['corto', 'con espacio', 'piñón.dulce', 'demasiado.largo.para.alias']) {
      typeInto(el, '#accountAlias', invalid);
      await submit();
      expect(el.textContent).toContain('De 6 a 20 caracteres');
    }
    httpTesting.expectNone(URL);
  });

  it('closes without saving when the alias did not change', async () => {
    const { el, submit } = await render();
    typeInto(el, '#accountAlias', 'MELBA.AHORROS');
    await submit();
    httpTesting.expectNone(URL);
    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('explains a taken or reserved alias and stays open', async () => {
    const { fixture, el, submit } = await render();
    typeInto(el, '#accountAlias', 'ocupado.alias');
    await submit();
    httpTesting.expectOne(URL).flush(null, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('ya lo usa otra cuenta');

    typeInto(el, '#accountAlias', 'vin-12345678');
    await submit();
    httpTesting
      .expectOne(URL)
      .flush({ code: 'ALIAS_RESERVED' }, { status: 422, statusText: 'Unprocessable Entity' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('reservado');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
