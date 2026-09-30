import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AlertSettings, AppNotification } from '../models/notification.model';
import { Page } from '../models/page.model';

/** The bell (any signed-in user) and the client's alert settings (CLIENT only). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clients/current/notifications`;
  private readonly alertsUrl = `${environment.apiUrl}/clients/current/alerts`;

  /** Newest first. */
  page(page = 0, size = 10): Observable<Page<AppNotification>> {
    return this.http.get<Page<AppNotification>>(this.baseUrl, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/unread-count`);
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/read`, null);
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/read-all`, null);
  }

  getAlerts(): Observable<AlertSettings> {
    return this.http.get<AlertSettings>(this.alertsUrl);
  }

  /** 400 for a threshold below 0.01 or with more than 2 decimals. */
  updateAlerts(settings: AlertSettings): Observable<AlertSettings> {
    return this.http.put<AlertSettings>(this.alertsUrl, settings);
  }
}
