import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  const base = '/api/clients/current/notifications';
  let service: NotificationService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    service = TestBed.inject(NotificationService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('pages the inbox', () => {
    service.page(1, 5).subscribe();
    const req = httpTesting.expectOne((r) => r.url === base);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('5');
  });

  it('counts, and marks one or all as read', () => {
    service.unreadCount().subscribe();
    httpTesting.expectOne(`${base}/unread-count`);
    service.markRead(3).subscribe();
    expect(httpTesting.expectOne(`${base}/3/read`).request.method).toBe('POST');
    service.markAllRead().subscribe();
    expect(httpTesting.expectOne(`${base}/read-all`).request.method).toBe('POST');
  });

  it('reads and replaces the alert settings', () => {
    const settings = {
      lowBalanceThreshold: 1000,
      largeMovementThreshold: null,
      loginAlerts: true,
      emailAlerts: false,
    };
    service.getAlerts().subscribe();
    expect(httpTesting.expectOne('/api/clients/current/alerts').request.method).toBe('GET');
    service.updateAlerts(settings).subscribe();
    const req = httpTesting.expectOne('/api/clients/current/alerts');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(settings);
  });
});
