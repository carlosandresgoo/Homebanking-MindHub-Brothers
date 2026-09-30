import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';

import { ScheduledTransfer } from '../../core/models/scheduled-transfer.model';
import { ACCOUNTS } from '../../testing/loan-fixtures';
import { provideTestDefaults } from '../../testing/providers';
import { ScheduleDialog } from './schedule-dialog/schedule-dialog';
import { ScheduledTransfers } from './scheduled-transfers';

const URL = '/api/clients/current/scheduled-transfers';
const RENT: ScheduledTransfer = {
  id: 1,
  sourceAccountId: 11,
  sourceAccountNumber: 'VIN001',
  targetAccountNumber: 'VIN999',
  targetHolder: 'Lucía P.',
  amount: 25000,
  description: 'Alquiler',
  frequency: 'MONTHLY',
  startDate: '2026-10-01',
  nextRun: '2026-11-01',
  runs: 1,
  maxRuns: 12,
  status: 'ACTIVE',
  lastRunAt: '2026-10-01T06:00:00',
  lastOutcome: 'FAILED',
  lastError: 'No había saldo suficiente en la cuenta de origen',
  createdAt: '2026-09-29T10:00:00',
};

describe('ScheduledTransfers', () => {
  let httpTesting: HttpTestingController;
  let open: ReturnType<typeof vi.spyOn>;
  let dialogResult: unknown;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScheduledTransfers],
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

  async function render(items: ScheduledTransfer[]) {
    const fixture = TestBed.createComponent(ScheduledTransfers);
    fixture.detectChanges();
    httpTesting.expectOne(URL).flush(items);
    httpTesting.expectOne('/api/clients/current/accounts').flush(ACCOUNTS);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('invites to schedule the first one', async () => {
    const { el } = await render([]);
    expect(el.textContent).toContain('No tenés transferencias programadas');
  });

  it('shows the next date, the progress and why the last run failed', async () => {
    const { el } = await render([RENT]);
    const item = el.querySelector('li.item')!;
    expect(item.textContent).toContain('Lucía P.');
    expect(item.textContent).toContain('Cada mes');
    expect(item.textContent).toContain('(2 de 12)');
    expect(item.querySelector('.failed')?.textContent).toContain('No había saldo suficiente');
    expect(item.querySelector('.status')?.textContent).toContain('Activa');
  });

  it('opens the dialog with my accounts and reloads after scheduling', async () => {
    const { fixture, el } = await render([]);
    dialogResult = RENT;
    el.querySelector<HTMLButtonElement>('.add-button')!.click();
    await fixture.whenStable();
    expect(open).toHaveBeenCalledWith(
      ScheduleDialog,
      expect.objectContaining({ data: { accounts: ACCOUNTS } }),
    );
    httpTesting.expectOne(URL).flush([RENT]);
  });

  it('pauses, and cancels only after confirming', async () => {
    const { fixture, el } = await render([RENT]);
    el.querySelector<HTMLButtonElement>('button.pause')!.click();
    httpTesting.expectOne(`${URL}/1/pause`).flush({ ...RENT, status: 'PAUSED' });
    httpTesting.expectOne(URL).flush([{ ...RENT, status: 'PAUSED' }]);
    await fixture.whenStable();
    expect(el.querySelector('button.resume')).not.toBeNull();

    dialogResult = false;
    el.querySelector<HTMLButtonElement>('button.cancel')!.click();
    httpTesting.expectNone(`${URL}/1`);

    dialogResult = true;
    el.querySelector<HTMLButtonElement>('button.cancel')!.click();
    const req = httpTesting.expectOne(`${URL}/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...RENT, status: 'CANCELLED' });
    httpTesting.expectOne(URL).flush([{ ...RENT, status: 'CANCELLED', nextRun: null }]);
    await fixture.whenStable();
    expect(el.querySelector('button.cancel')).toBeNull();
  });
});
