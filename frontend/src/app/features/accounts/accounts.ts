import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, startWith } from 'rxjs';

import { ClientService } from '../../core/api/client.service';
import { Client } from '../../core/models/client.model';

type ClientsState =
  | { status: 'loading' }
  | { status: 'loaded'; clients: Client[] }
  | { status: 'error' };

@Component({
  selector: 'app-accounts',
  imports: [DatePipe],
  templateUrl: './accounts.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accounts {
  private readonly clientService = inject(ClientService);

  protected readonly state = toSignal(
    this.clientService.getClients().pipe(
      map((clients): ClientsState => ({ status: 'loaded', clients })),
      catchError(() => of<ClientsState>({ status: 'error' })),
      startWith<ClientsState>({ status: 'loading' }),
    ),
    { requireSync: true },
  );
}
