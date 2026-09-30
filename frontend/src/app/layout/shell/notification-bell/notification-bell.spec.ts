import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { Router } from '@angular/router';

import { AppNotification } from '../../../core/models/notification.model';
import { provideTestDefaults } from '../../../testing/providers';
import { NotificationBell } from './notification-bell';

const BASE = '/api/clients/current/notifications';
const RECEIVED: AppNotification = {
  id: 7,
  type: 'TRANSFER_RECEIVED',
  title: 'Recibiste $ 250,00',
  message: 'De la cuenta ···0001',
  link: '/accounts/5',
  createdAt: '2026-09-29T21:40:00',
  read: false,
};
const LOGIN: AppNotification = {
  id: 6,
  type: 'LOGIN',
  title: 'Nuevo ingreso a tu cuenta',
  message: 'Desde Chrome en Windows',
  link: '/profile',
  createdAt: '2026-09-29T20:00:00',
  read: true,
};

describe('NotificationBell', () => {
  let httpTesting: HttpTestingController;
  let current: ComponentFixture<NotificationBell>;

  async function render(unread: number) {
    await TestBed.configureTestingModule({
      imports: [NotificationBell],
      providers: provideTestDefaults(),
    }).compileComponents();
    httpTesting = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(NotificationBell);
    current = fixture;
    fixture.detectChanges();
    httpTesting.expectOne(`${BASE}/unread-count`).flush({ count: unread });
    await fixture.whenStable();
    const loader = TestbedHarnessEnvironment.loader(fixture);
    return { fixture, el: fixture.nativeElement as HTMLElement, loader };
  }

  async function openWith(
    loader: ReturnType<typeof TestbedHarnessEnvironment.loader>,
    items: AppNotification[],
  ) {
    const menu = await loader.getHarness(MatMenuHarness);
    await menu.open();
    httpTesting
      .expectOne((r) => r.url === BASE)
      .flush({ content: items, page: 0, size: 10, totalElements: items.length, totalPages: 1 });
    await current.whenStable();
    return menu;
  }

  afterEach(() => httpTesting.verify());

  it('shows how many are unread in the badge and the label', async () => {
    const { el } = await render(3);
    expect(el.querySelector('.mat-badge-content')?.textContent).toBe('3');
    expect(el.querySelector('.bell')?.getAttribute('aria-label')).toBe(
      'Notificaciones: 3 sin leer',
    );
  });

  it('hides the badge when everything is read', async () => {
    const { el } = await render(0);
    expect(el.querySelector('.mat-badge-hidden')).not.toBeNull();
    expect(el.querySelector('.bell')?.getAttribute('aria-label')).toBe('Notificaciones');
  });

  it('lists the latest ones and opens an unread one, marking it read', async () => {
    const { loader } = await render(1);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const menu = await openWith(loader, [RECEIVED, LOGIN]);

    const items = await menu.getItems();
    expect(await items[0].getText()).toContain('Recibiste $ 250,00');
    expect(await items[1].getText()).toContain('Nuevo ingreso a tu cuenta');

    await items[0].click();
    httpTesting.expectOne(`${BASE}/7/read`).flush(null);
    httpTesting.expectOne(`${BASE}/unread-count`).flush({ count: 0 });
    expect(navigate).toHaveBeenCalledWith('/accounts/5');
  });

  it('does not mark again one that was already read', async () => {
    const { loader } = await render(0);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const menu = await openWith(loader, [LOGIN]);
    await (await menu.getItems())[0].click();
    httpTesting.expectNone(`${BASE}/6/read`);
  });

  it('marks all as read', async () => {
    const { fixture, loader } = await render(2);
    await openWith(loader, [RECEIVED]);
    const button = document.querySelector<HTMLButtonElement>('.read-all')!;
    button.click();
    httpTesting.expectOne(`${BASE}/read-all`).flush(null);
    httpTesting.expectOne(`${BASE}/unread-count`).flush({ count: 0 });
    await fixture.whenStable();
    expect(document.querySelector('.item.unread')).toBeNull();
  });

  it('says so when there is nothing yet', async () => {
    const { loader } = await render(0);
    await openWith(loader, []);
    expect(document.querySelector('.hb-notifications-panel')?.textContent).toContain(
      'No tenés notificaciones.',
    );
  });
});
