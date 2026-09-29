import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Client, CreateClientRequest } from '../models/client.model';

@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/clients`;

  /** ADMIN only. */
  getClients(): Observable<Client[]> {
    return this.http.get<Client[]>(this.baseUrl);
  }

  /** ADMIN only. */
  getClient(id: number): Observable<Client> {
    return this.http.get<Client>(`${this.baseUrl}/${id}`);
  }

  /** The logged-in client's own data. */
  getCurrentClient(): Observable<Client> {
    return this.http.get<Client>(`${this.baseUrl}/current`);
  }

  /** ADMIN only: `false` blocks the client (ends its sessions), `true` unblocks it. */
  setStatus(id: number, enabled: boolean): Observable<Client> {
    return this.http.patch<Client>(`${this.baseUrl}/${id}/status`, { enabled });
  }

  /** ADMIN only: turns 2FA off for a client who lost their phone. */
  resetTwoFactor(id: number): Observable<Client> {
    return this.http.delete<Client>(`${this.baseUrl}/${id}/2fa`);
  }

  /** ADMIN only. */
  createClient(request: CreateClientRequest): Observable<Client> {
    return this.http.post<Client>(this.baseUrl, request);
  }
}
