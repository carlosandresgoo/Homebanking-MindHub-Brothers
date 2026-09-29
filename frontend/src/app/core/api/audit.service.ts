import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuditEvent, AuditQuery, Page } from '../models/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  /** ADMIN only; newest first. */
  search(query: AuditQuery): Observable<Page<AuditEvent>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    for (const key of ['actor', 'action', 'from', 'to'] as const) {
      const value = query[key];
      if (value) {
        params = params.set(key, value);
      }
    }
    return this.http.get<Page<AuditEvent>>(`${environment.apiUrl}/admin/audit`, { params });
  }
}
