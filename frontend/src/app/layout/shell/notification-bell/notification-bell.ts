import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';
import { Subject, catchError, interval, map, merge, of, startWith, switchMap } from 'rxjs';

import { NotificationService } from '../../../core/api/notification.service';
import { AppNotification, NOTIFICATION_ICON } from '../../../core/models/notification.model';

/** How often the unread count is refreshed while the app is open. */
export const POLL_MS = 60_000;

/** The bell: unread count (polled) and the latest notifications in a menu. */
@Component({
  selector: 'app-notification-bell',
  imports: [
    DatePipe,
    MatBadgeModule,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
  ],
  template: `
    <button
      mat-icon-button
      class="bell"
      [matMenuTriggerFor]="panel"
      (menuOpened)="load()"
      [attr.aria-label]="label()"
    >
      <mat-icon
        aria-hidden="true"
        [matBadge]="badge()"
        [matBadgeHidden]="unread() === 0"
        matBadgeColor="warn"
        matBadgeSize="small"
        >notifications</mat-icon
      >
    </button>

    <mat-menu #panel="matMenu" xPosition="before" class="hb-notifications-panel">
      <div
        class="panel-header"
        (click)="$event.stopPropagation()"
        (keydown)="$event.stopPropagation()"
      >
        <strong>Notificaciones</strong>
        @if (unread() > 0) {
          <button mat-button type="button" class="read-all" (click)="readAll()">
            Marcar todas como leídas
          </button>
        }
      </div>
      <mat-divider />
      @let list = items();
      @if (list === null) {
        <p class="empty">Cargando…</p>
      } @else if (failed()) {
        <p class="empty" role="alert">No pudimos cargar tus notificaciones.</p>
      } @else if (list.length === 0) {
        <p class="empty">No tenés notificaciones.</p>
      } @else {
        @for (n of list; track n.id) {
          <button mat-menu-item class="item" [class.unread]="!n.read" (click)="select(n)">
            <mat-icon>{{ icons[n.type] }}</mat-icon>
            <span class="text">
              <span class="title">{{ n.title }}</span>
              <span class="message">{{ n.message }}</span>
              <span class="when">{{ n.createdAt | date: "d 'de' MMM, HH:mm" }}</span>
            </span>
          </button>
        }
      }
    </mat-menu>
  `,
  styles: `
    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 4px 8px 8px 16px;
      font: var(--mat-sys-title-small);
    }
    .empty {
      margin: 0;
      padding: 16px;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-medium);
    }
    .item {
      height: auto;
      min-height: 64px;
      padding-top: 10px;
      padding-bottom: 10px;
      align-items: flex-start;
    }
    .item mat-icon {
      margin-top: 2px;
      color: var(--mat-sys-on-surface-variant);
    }
    .item.unread {
      background: var(--mat-sys-secondary-container);
    }
    .item.unread mat-icon {
      color: var(--mat-sys-primary);
    }
    .text {
      display: flex;
      flex-direction: column;
      gap: 2px;
      white-space: normal;
    }
    .title {
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface);
    }
    .message {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
    .when {
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-outline);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationBell {
  private readonly api = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly refresh$ = new Subject<void>();

  protected readonly icons = NOTIFICATION_ICON;

  /** Refreshed right away, every minute and after reading; 0 when it cannot be loaded. */
  protected readonly unread = toSignal(
    merge(interval(POLL_MS).pipe(startWith(0)), this.refresh$).pipe(
      switchMap(() =>
        this.api.unreadCount().pipe(
          map((r) => r.count),
          catchError(() => of(0)),
        ),
      ),
    ),
    { initialValue: 0 },
  );
  protected readonly badge = computed(() => (this.unread() > 99 ? '99+' : String(this.unread())));
  protected readonly label = computed(() =>
    this.unread() > 0 ? `Notificaciones: ${this.unread()} sin leer` : 'Notificaciones',
  );

  /** Null while loading the latest ones (when the menu opens). */
  protected readonly items = signal<AppNotification[] | null>(null);
  protected readonly failed = signal(false);

  protected load(): void {
    this.items.set(null);
    this.failed.set(false);
    this.api.page(0, 10).subscribe({
      next: (page) => this.items.set(page.content),
      error: () => {
        this.failed.set(true);
        this.items.set([]);
      },
    });
  }

  protected select(notification: AppNotification): void {
    if (!notification.read) {
      this.api.markRead(notification.id).subscribe({ next: () => this.refresh$.next() });
    }
    if (notification.link) {
      void this.router.navigateByUrl(notification.link);
    }
  }

  protected readAll(): void {
    this.api.markAllRead().subscribe({
      next: () => {
        this.items.update((list) => list?.map((n) => ({ ...n, read: true })) ?? list);
        this.refresh$.next();
      },
    });
  }
}
