import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Card, IssueCardRequest, IssuedCard } from '../models/card.model';

@Injectable({ providedIn: 'root' })
export class CardService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  getMyCards(): Observable<Card[]> {
    return this.http.get<Card[]>(`${this.api}/clients/current/cards`);
  }

  /** 409 when an active card of the same type and colour already exists. */
  issueCard(request: IssueCardRequest): Observable<IssuedCard> {
    return this.http.post<IssuedCard>(`${this.api}/clients/current/cards`, request);
  }

  deactivateCard(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/cards/${id}`);
  }
}
