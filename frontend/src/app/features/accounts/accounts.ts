import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { ClientService } from '../../core/api/client.service';
import { toLoadState } from '../../core/utils/load-state';

@Component({
  selector: 'app-accounts',
  imports: [DatePipe],
  templateUrl: './accounts.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accounts {
  private readonly clientService = inject(ClientService);

  protected readonly state = toSignal(toLoadState(this.clientService.getClients()), {
    requireSync: true,
  });
}
