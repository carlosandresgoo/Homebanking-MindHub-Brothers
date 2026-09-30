import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { Client } from '../../core/models/client.model';
import { provideTestDefaults, typeInto } from '../../testing/providers';
import { Manager } from './manager';

const CLIENTS: Client[] = [
  {
    id: 1,
    name: 'Melba',
    lastName: 'Morel',
    email: 'melba@gmail.com',
    role: 'CLIENT',
    accounts: [
      {
        id: 1,
        number: 'vin001',
        cbu: '9990001800000000000017',
        alias: 'vin001.test',
        creationDate: '2026-09-29T10:00:00',
        balance: 5000,
      },
      {
        id: 2,
        number: 'vin002',
        cbu: '9990001800000000000017',
        alias: 'vin002.test',
        creationDate: '2026-09-30T10:00:00',
        balance: 7500,
      },
    ],
  },
  {
    id: 2,
    name: 'Admin',
    lastName: 'Mindhub',
    email: 'admin@mindhub.com',
    role: 'ADMIN',
    accounts: [],
  },
];

describe('Manager', () => {
  let httpTesting: HttpTestingController;
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    dialog.open.mockReset();
    await TestBed.configureTestingModule({
      imports: [Manager],
      providers: [...provideTestDefaults(), { provide: MatDialog, useValue: dialog }],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  async function renderLoaded() {
    const fixture = TestBed.createComponent(Manager);
    fixture.detectChanges();
    httpTesting.expectOne('/api/clients').flush(CLIENTS);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows the stats: clients, accounts and managed balance', async () => {
    const { el } = await renderLoaded();
    const stats = Array.from(el.querySelectorAll('.stat strong')).map((s) => s.textContent?.trim());
    expect(stats[0]).toBe('1');
    expect(stats[1]).toBe('2');
    expect(stats[2]).toMatch(/12\.500,00/);
  });

  it('lists every client with role and total balance', async () => {
    const { el } = await renderLoaded();
    const rows = el.querySelectorAll('tr.client-row');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Melba Morel');
    expect(rows[0].textContent).toContain('melba@gmail.com');
    expect(rows[0].textContent).toMatch(/12\.500,00/);
    expect(rows[1].textContent).toContain('Administrador');
  });

  it('filters by name or email', async () => {
    const { fixture, el } = await renderLoaded();
    typeInto(el, '.search input', 'MINDHUB');
    await fixture.whenStable();

    const rows = el.querySelectorAll('tr.client-row');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('admin@mindhub.com');

    typeInto(el, '.search input', 'nadie');
    await fixture.whenStable();
    expect(el.querySelector('.empty')?.textContent).toContain('No hay clientes que coincidan');
  });

  it("expands a row to show the client's accounts", async () => {
    const { fixture, el } = await renderLoaded();
    expect(el.querySelector('.detail')).toBeNull();

    el.querySelector<HTMLButtonElement>('button[aria-label="Ver cuentas de Melba Morel"]')!.click();
    await fixture.whenStable();

    const accounts = el.querySelectorAll('.detail li');
    expect(accounts).toHaveLength(2);
    expect(accounts[0].textContent).toContain('VIN001');
  });

  it('reloads the list after a client is created in the dialog', async () => {
    const { fixture, el } = await renderLoaded();
    const created: Client = {
      id: 3,
      name: 'Chloe',
      lastName: 'Obrian',
      email: 'chloe@test.com',
      role: 'CLIENT',
      accounts: [],
    };
    dialog.open.mockReturnValue({ afterClosed: () => of(created) });

    el.querySelector<HTMLButtonElement>('button.new-client')!.click();
    httpTesting.expectOne('/api/clients').flush([...CLIENTS, created]);
    await fixture.whenStable();

    expect(dialog.open).toHaveBeenCalled();
    expect(el.querySelectorAll('tr.client-row')).toHaveLength(3);
  });

  it('shows each status and blocks a client after confirmation', async () => {
    const { fixture, el } = await renderLoaded();
    expect(el.querySelector('tr.client-row .status')?.textContent?.trim()).toBe('Activo');
    // Admins have no block button.
    expect(el.querySelectorAll('.status-action')).toHaveLength(1);
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });

    el.querySelector<HTMLButtonElement>('button[aria-label="Bloquear a Melba Morel"]')!.click();

    const req = httpTesting.expectOne('/api/clients/1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ enabled: false });
    req.flush({ ...CLIENTS[0], enabled: false });
    httpTesting.expectOne('/api/clients').flush([{ ...CLIENTS[0], enabled: false }, CLIENTS[1]]);
    await fixture.whenStable();

    expect(el.querySelector('tr.client-row .status')?.textContent?.trim()).toBe('Bloqueado');
    expect(el.querySelector('button[aria-label="Desbloquear a Melba Morel"]')).not.toBeNull();
  });

  it('labels the paginator in Spanish', async () => {
    const { el } = await renderLoaded();
    expect(el.querySelector('mat-paginator')?.textContent).toContain('Filas por página');
    expect(el.querySelector('mat-paginator')?.textContent).toContain('1 – 2 de 2');
  });

  it('does not reload when the dialog is cancelled', async () => {
    const { el } = await renderLoaded();
    dialog.open.mockReturnValue({ afterClosed: () => of(undefined) });

    el.querySelector<HTMLButtonElement>('button.new-client')!.click();

    httpTesting.expectNone('/api/clients');
  });
});
