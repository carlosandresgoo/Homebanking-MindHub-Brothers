import { HttpRequest } from '@angular/common/http';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { AccountDetail, Transaction } from '../../core/models/account.model';
import { Role } from '../../core/models/auth.model';
import { Page } from '../../core/models/page.model';
import { provideTestDefaults, typeInto } from '../../testing/providers';
import { AccountDetailPage } from './account-detail';

const ACCOUNT: AccountDetail = {
  id: 7,
  number: 'VIN001',
  cbu: '9990001800000000000017',
  alias: 'vin001.test',
  creationDate: '2026-08-30T10:00:00',
  balance: 5000,
};

const MOVEMENTS: Transaction[] = [
  {
    id: 2,
    type: 'DEBIT',
    category: 'OTHER',
    amount: 1200,
    description: 'Alquiler',
    date: '2026-09-21T09:00:00',
    balanceAfter: 5000,
  },
  {
    id: 1,
    type: 'CREDIT',
    category: 'DEPOSIT',
    amount: 6200,
    description: 'Depósito inicial',
    date: '2026-08-30T10:00:00',
    balanceAfter: 6200,
  },
];

function page(content: Transaction[], totalElements = content.length): Page<Transaction> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

const isMovements = (req: HttpRequest<unknown>) =>
  req.method === 'GET' && req.url === '/api/accounts/7/transactions';

describe('AccountDetailPage', () => {
  let httpTesting: HttpTestingController;
  const dialog = { open: vi.fn() };

  beforeEach(async () => {
    dialog.open.mockReset();
    await TestBed.configureTestingModule({
      imports: [AccountDetailPage],
      providers: [...provideTestDefaults(), { provide: MatDialog, useValue: dialog }],
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  function logInAs(role: Role): void {
    TestBed.inject(AuthService).login({ email: 'x@test.com', password: 'x' }).subscribe();
    httpTesting
      .expectOne('/api/auth/login')
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 900, role });
  }

  async function render(
    account: AccountDetail | null,
    role: Role = 'CLIENT',
    movements = page(MOVEMENTS),
  ) {
    logInAs(role);
    const fixture = TestBed.createComponent(AccountDetailPage);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    const req = httpTesting.expectOne('/api/accounts/7');
    const movementsReq = httpTesting.expectOne(isMovements);
    if (account) {
      req.flush(account);
      movementsReq.flush(movements);
    } else {
      req.flush(null, { status: 404, statusText: 'Not Found' });
      movementsReq.flush(null, { status: 404, statusText: 'Not Found' });
    }
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  /** Waits for the filters' debounce and returns the resulting movements request. */
  async function nextMovementsRequest(
    fixture: ComponentFixture<AccountDetailPage>,
  ): Promise<TestRequest> {
    await new Promise((resolve) => setTimeout(resolve, 350));
    await fixture.whenStable();
    return httpTesting.expectOne(isMovements);
  }

  it('shows the balance and the movements with sign, each linking to its receipt', async () => {
    const { el } = await render(ACCOUNT);

    expect(el.querySelector('h1')?.textContent).toContain('VIN001');
    expect(el.querySelector('.balance')?.textContent).toMatch(/5\.000,00/);
    const movements = el.querySelectorAll<HTMLAnchorElement>('a.movement');
    expect(movements).toHaveLength(2);
    expect(movements[0].textContent).toContain('Alquiler');
    expect(movements[0].textContent).toMatch(/−\s*\$\s*1\.200,00/);
    expect(movements[0].getAttribute('href')).toBe('/movements/2');
    expect(movements[1].classList).toContain('credit');
    expect(movements[1].textContent).toMatch(/\+\s*\$\s*6\.200,00/);
    expect(movements[1].textContent).toContain('Depósito');
  });

  it('asks the API for page 0 of 20 by default', async () => {
    logInAs('CLIENT');
    const fixture = TestBed.createComponent(AccountDetailPage);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    httpTesting.expectOne('/api/accounts/7').flush(ACCOUNT);
    const req = httpTesting.expectOne(isMovements);
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('type')).toBe(false);
    req.flush(page(MOVEMENTS));
  });

  it('filters by text and dates, and explains when nothing matches', async () => {
    const { fixture, el } = await render(ACCOUNT);
    typeInto(el, '#q', 'alquiler');
    typeInto(el, '#from', '2026-09-01');
    const req = await nextMovementsRequest(fixture);
    expect(req.request.params.get('q')).toBe('alquiler');
    expect(req.request.params.get('from')).toBe('2026-09-01');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
    await fixture.whenStable();

    expect(el.textContent).toContain('No hay movimientos con esos filtros');
  });

  it('does not query an impossible date range', async () => {
    const { fixture, el } = await render(ACCOUNT);
    typeInto(el, '#from', '2026-09-10');
    typeInto(el, '#to', '2026-09-01');
    await new Promise((resolve) => setTimeout(resolve, 350));
    await fixture.whenStable();

    httpTesting.expectNone(isMovements);
    expect(el.querySelector('.range-error')?.textContent).toContain('no puede ser posterior');
  });

  /** Opens the "Descargar" menu and picks an option (rendered in an overlay). */
  async function pick(fixture: ComponentFixture<AccountDetailPage>, option: string) {
    const menu = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatMenuHarness);
    await menu.open();
    await menu.clickItem({ text: new RegExp(option) });
  }

  it('exports the filtered movements as a CSV download', async () => {
    const { fixture } = await render(ACCOUNT);
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:csv');
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    await pick(fixture, 'CSV');
    const req = httpTesting.expectOne((r) => r.url === '/api/accounts/7/transactions/export');
    expect(req.request.params.get('format')).toBe('csv');
    req.flush(new Blob(['Fecha;...']), {
      headers: { 'Content-Disposition': 'attachment; filename="movimientos-VIN001-20260929.csv"' },
    });
    await fixture.whenStable();

    expect(createUrl).toHaveBeenCalled();
    const link = click.mock.contexts[0] as HTMLAnchorElement;
    expect(link.download).toBe('movimientos-VIN001-20260929.csv');
    createUrl.mockRestore();
    click.mockRestore();
  });

  it('downloads Excel and the PDF statement of the filtered dates', async () => {
    const { fixture, el } = await render(ACCOUNT);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    await pick(fixture, 'Excel');
    const xlsx = httpTesting.expectOne((r) => r.url === '/api/accounts/7/transactions/export');
    expect(xlsx.request.params.get('format')).toBe('xlsx');
    xlsx.flush(new Blob(['PK']));
    await fixture.whenStable();
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('movimientos-VIN001.xlsx');

    typeInto(el, '#from', '2026-09-01');
    typeInto(el, '#to', '2026-09-15');
    (await nextMovementsRequest(fixture)).flush(page(MOVEMENTS));
    await fixture.whenStable();

    await pick(fixture, 'PDF');
    const pdf = httpTesting.expectOne((r) => r.url === '/api/accounts/7/statement');
    expect(pdf.request.params.get('from')).toBe('2026-09-01');
    expect(pdf.request.params.get('to')).toBe('2026-09-15');
    pdf.flush(new Blob(['%PDF']), {
      headers: {
        'Content-Disposition': 'attachment; filename="resumen-VIN001-20260901-20260915.pdf"',
      },
    });
    await fixture.whenStable();
    expect((click.mock.contexts[1] as HTMLAnchorElement).download).toBe(
      'resumen-VIN001-20260901-20260915.pdf',
    );
    click.mockRestore();
  });

  it('shows the paginator only when there is more than one short page', async () => {
    const few = await render(ACCOUNT);
    expect(few.el.querySelector('mat-paginator')).toBeNull();
  });

  it('does not let the owner close an account with money', async () => {
    const { el } = await render(ACCOUNT);
    expect(el.querySelector<HTMLButtonElement>('.close-button')!.disabled).toBe(true);
    expect(el.textContent).toContain('el saldo debe ser');
  });

  it('closes an empty account after confirmation and goes back', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    dialog.open.mockReturnValue({ afterClosed: () => of(true) });
    const { el } = await render({ ...ACCOUNT, balance: 0 });

    el.querySelector<HTMLButtonElement>('.close-button')!.click();
    const req = httpTesting.expectOne('/api/accounts/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(navigate).toHaveBeenCalledWith('/accounts');
  });

  it('keeps the account when the dialog is cancelled', async () => {
    dialog.open.mockReturnValue({ afterClosed: () => of(false) });
    const { el } = await render({ ...ACCOUNT, balance: 0 });

    el.querySelector<HTMLButtonElement>('.close-button')!.click();

    httpTesting.expectNone({ method: 'DELETE', url: '/api/accounts/7' });
  });

  it('admins see the account read-only with a link back to Clientes', async () => {
    const { el } = await render(ACCOUNT, 'ADMIN');
    expect(el.querySelector('.close-button')).toBeNull();
    expect(el.querySelector('a.back')?.getAttribute('href')).toBe('/manager');
  });

  it('shows a not-found state for accounts that are not mine', async () => {
    const { el } = await render(null);
    expect(el.querySelector('[role="alert"]')?.textContent).toContain('No encontramos esta cuenta');
  });
});
