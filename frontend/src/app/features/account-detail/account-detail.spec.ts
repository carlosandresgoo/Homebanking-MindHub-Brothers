import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { AccountDetail } from '../../core/models/account.model';
import { Role } from '../../core/models/auth.model';
import { provideTestDefaults } from '../../testing/providers';
import { AccountDetailPage } from './account-detail';

const ACCOUNT: AccountDetail = {
  id: 7,
  number: 'VIN001',
  creationDate: '2026-08-30T10:00:00',
  balance: 5000,
  transactions: [
    {
      id: 2,
      type: 'DEBIT',
      amount: 1200,
      description: 'Alquiler',
      date: '2026-09-21T09:00:00',
      balanceAfter: 5000,
    },
    {
      id: 1,
      type: 'CREDIT',
      amount: 6200,
      description: 'Depósito inicial',
      date: '2026-08-30T10:00:00',
      balanceAfter: 6200,
    },
  ],
};

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

  async function render(account: AccountDetail | null, role: Role = 'CLIENT') {
    logInAs(role);
    const fixture = TestBed.createComponent(AccountDetailPage);
    fixture.componentRef.setInput('id', '7');
    fixture.detectChanges();
    const req = httpTesting.expectOne('/api/accounts/7');
    if (account) {
      req.flush(account);
    } else {
      req.flush(null, { status: 404, statusText: 'Not Found' });
    }
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows the balance and the movements with sign', async () => {
    const { el } = await render(ACCOUNT);

    expect(el.querySelector('h1')?.textContent).toContain('VIN001');
    expect(el.querySelector('.balance')?.textContent).toMatch(/5\.000,00/);
    const movements = el.querySelectorAll('.movement');
    expect(movements).toHaveLength(2);
    expect(movements[0].textContent).toContain('Alquiler');
    expect(movements[0].textContent).toMatch(/−\s*\$\s*1\.200,00/);
    expect(movements[1].classList).toContain('credit');
    expect(movements[1].textContent).toMatch(/\+\s*\$\s*6\.200,00/);
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
