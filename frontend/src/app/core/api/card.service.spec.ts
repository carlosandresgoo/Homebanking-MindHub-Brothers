import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { provideTestDefaults } from '../../testing/providers';
import { CardService } from './card.service';

describe('CardService', () => {
  let service: CardService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    service = TestBed.inject(CardService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('lists my cards', () => {
    service.getMyCards().subscribe();
    expect(httpTesting.expectOne('/api/clients/current/cards').request.method).toBe('GET');
  });

  it('issues a card', () => {
    service.issueCard({ type: 'CREDIT', color: 'GOLD' }).subscribe();
    const req = httpTesting.expectOne('/api/clients/current/cards');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ type: 'CREDIT', color: 'GOLD' });
  });

  it('deactivates a card', () => {
    service.deactivateCard(3).subscribe();
    expect(httpTesting.expectOne('/api/cards/3').request.method).toBe('DELETE');
  });
});
