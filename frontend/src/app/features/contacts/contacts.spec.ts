import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Contact } from '../../core/models/contact.model';
import { provideTestDefaults } from '../../testing/providers';
import { ContactDialog } from './contact-dialog/contact-dialog';
import { Contacts } from './contacts';

const URL = '/api/clients/current/contacts';
const LUCIA: Contact = {
  id: 1,
  alias: 'Lucía',
  accountNumber: 'VIN-27905812',
  holderDisplay: 'Lucía P.',
  createdAt: '2026-09-01T10:00:00',
};
const ALQUILER: Contact = { ...LUCIA, id: 2, alias: 'Alquiler', accountNumber: 'VIN-11112222' };

describe('Contacts', () => {
  let httpTesting: HttpTestingController;
  let open: ReturnType<typeof vi.spyOn>;
  let dialogResult: unknown;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Contacts],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    dialogResult = undefined;
    open = vi
      .spyOn(TestBed.inject(MatDialog), 'open')
      .mockImplementation(
        () => ({ afterClosed: () => of(dialogResult) }) as ReturnType<MatDialog['open']>,
      );
  });

  afterEach(() => httpTesting.verify());

  async function render(contacts: Contact[]) {
    const fixture = TestBed.createComponent(Contacts);
    fixture.detectChanges();
    httpTesting.expectOne(URL).flush(contacts);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('invites to add the first recipient', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('Todavía no guardaste destinatarios');
  });

  it('lists recipients with a link to transfer to each', async () => {
    const { el } = await render([ALQUILER, LUCIA]);
    const items = el.querySelectorAll('li.contact');
    expect(items).toHaveLength(2);
    expect(items[1].textContent).toContain('Lucía P.');
    expect(items[1].querySelector('a.transfer')?.getAttribute('href')).toBe(
      '/transfers?to=VIN-27905812',
    );
  });

  it('adds a recipient through the dialog and reloads', async () => {
    const { fixture, el } = await render([]);
    dialogResult = LUCIA;
    el.querySelector<HTMLButtonElement>('.add-button')!.click();
    await fixture.whenStable();

    expect(open).toHaveBeenCalledWith(ContactDialog, expect.objectContaining({ data: {} }));
    httpTesting.expectOne(URL).flush([LUCIA]);
    await fixture.whenStable();
    expect(el.querySelectorAll('li.contact')).toHaveLength(1);
  });

  it('deletes after confirmation', async () => {
    const { fixture, el } = await render([LUCIA]);
    dialogResult = true;
    el.querySelector<HTMLButtonElement>('button[aria-label="Borrar a Lucía"]')!.click();

    httpTesting.expectOne(`${URL}/1`).flush(null, { status: 204, statusText: 'No Content' });
    httpTesting.expectOne(URL).flush([]);
    await fixture.whenStable();
    expect(el.textContent).toContain('Todavía no guardaste destinatarios');
  });

  it('keeps everything when the deletion is cancelled', async () => {
    const { el } = await render([LUCIA]);
    dialogResult = false;
    el.querySelector<HTMLButtonElement>('button[aria-label="Borrar a Lucía"]')!.click();
    httpTesting.expectNone(`${URL}/1`);
  });
});
