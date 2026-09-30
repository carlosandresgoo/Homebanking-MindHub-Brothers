import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { MovementService } from './movement.service';

describe('MovementService', () => {
  let service: MovementService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    service = TestBed.inject(MovementService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('pages with only the filters that are set', () => {
    service.page(7, { from: '2026-09-01', q: '  ' }, 1, 20).subscribe();
    const req = httpTesting.expectOne((r) => r.url === '/api/accounts/7/transactions');
    expect(req.request.params.keys().sort()).toEqual(['from', 'page', 'size']);
  });

  it('exports CSV by default and Excel on request, as files', () => {
    service.exportMovements(7, { type: 'DEBIT' }).subscribe();
    const csv = httpTesting.expectOne((r) => r.url === '/api/accounts/7/transactions/export');
    expect(csv.request.params.get('format')).toBe('csv');
    expect(csv.request.params.get('type')).toBe('DEBIT');
    expect(csv.request.responseType).toBe('blob');

    service.exportMovements(7, {}, 'xlsx').subscribe();
    const xlsx = httpTesting.expectOne((r) => r.url === '/api/accounts/7/transactions/export');
    expect(xlsx.request.params.get('format')).toBe('xlsx');
  });

  it('asks for the statement of a period, or the default one', () => {
    service.statement(7, '2026-09-01', '2026-09-30').subscribe();
    const period = httpTesting.expectOne((r) => r.url === '/api/accounts/7/statement');
    expect(period.request.params.get('from')).toBe('2026-09-01');
    expect(period.request.params.get('to')).toBe('2026-09-30');

    service.statement(7).subscribe();
    expect(httpTesting.expectOne('/api/accounts/7/statement').request.params.keys()).toEqual([]);
  });
});
