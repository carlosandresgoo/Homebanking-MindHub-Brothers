import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatPaginatorHarness } from '@angular/material/paginator/testing';
import { MatSelectHarness } from '@angular/material/select/testing';

import { AuditEvent, Page } from '../../core/models/audit.model';
import { provideTestDefaults } from '../../testing/providers';
import { Audit } from './audit';

const EVENTS: AuditEvent[] = [
  {
    id: 2,
    occurredAt: '2026-09-29T15:00:00Z',
    actor: 'melba@gmail.com',
    actorRole: 'CLIENT',
    action: 'TRANSFER',
    target: 'VIN001 -> VIN002',
    outcome: 'SUCCESS',
    ip: '127.0.0.1',
    details: 'amount=250.00',
  },
  {
    id: 1,
    occurredAt: '2026-09-29T14:00:00Z',
    actor: 'melba@gmail.com',
    actorRole: null,
    action: 'LOGIN',
    target: null,
    outcome: 'FAILURE',
    ip: '127.0.0.1',
    details: 'bad credentials',
  },
];

function page(
  content: AuditEvent[],
  totalElements = content.length,
  pageIndex = 0,
): Page<AuditEvent> {
  return {
    content,
    page: pageIndex,
    size: 20,
    totalElements,
    totalPages: Math.ceil(totalElements / 20),
  };
}

describe('Audit', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    await TestBed.configureTestingModule({
      imports: [Audit],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.useRealTimers();
  });

  const auditRequest = (): TestRequest =>
    httpTesting.expectOne((req) => req.url === '/api/admin/audit');

  async function render() {
    const fixture = TestBed.createComponent(Audit);
    fixture.detectChanges();
    await fixture.whenStable();
    const first = auditRequest();
    expect(first.request.params.get('page')).toBe('0');
    expect(first.request.params.get('size')).toBe('20');
    first.flush(page(EVENTS, 45));
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('lists events with Spanish labels and outcome', async () => {
    const { el } = await render();
    const rows = el.querySelectorAll('tr.mat-mdc-row');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Transferencia');
    expect(rows[0].textContent).toContain('VIN001 -> VIN002');
    expect(rows[0].textContent).toContain('Éxito');
    expect(rows[1].textContent).toContain('Ingreso');
    expect(rows[1].querySelector('.outcome.failure')?.textContent).toContain('Fallo');
  });

  it('filters by action from the first page', async () => {
    const { fixture } = await render();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const action = await loader.getHarness(MatSelectHarness.with({ selector: '#action' }));
    await action.clickOptions({ text: 'Transferencia' });
    await vi.advanceTimersByTimeAsync(350);
    await fixture.whenStable();

    const req = auditRequest();
    expect(req.request.params.get('action')).toBe('TRANSFER');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([EVENTS[0]]));
  });

  it('requests the next page from the server', async () => {
    const { fixture } = await render();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    const paginator = await loader.getHarness(MatPaginatorHarness);
    expect(await paginator.getRangeLabel()).toBe('1 – 20 de 45');

    await paginator.goToNextPage();
    const req = auditRequest();
    expect(req.request.params.get('page')).toBe('1');
    req.flush(page(EVENTS, 45, 1));
  });
});
