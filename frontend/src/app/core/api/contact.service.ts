import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Contact } from '../models/contact.model';

/** The logged-in client's saved recipients. */
@Injectable({ providedIn: 'root' })
export class ContactService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clients/current/contacts`;

  getMine(): Observable<Contact[]> {
    return this.http.get<Contact[]>(this.baseUrl);
  }

  /** 404 unknown account; 422 own account or agenda full; 409 duplicate account or alias; 429 too many. */
  add(accountNumber: string, alias: string): Observable<Contact> {
    return this.http.post<Contact>(this.baseUrl, { accountNumber, alias });
  }

  /** 409 when another recipient already has that alias. */
  rename(id: number, alias: string): Observable<Contact> {
    return this.http.patch<Contact>(`${this.baseUrl}/${id}`, { alias });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
