import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { ClientService } from '../../core/api/client.service';
import { AuthService } from '../../core/auth/auth.service';
import { toLoadState } from '../../core/utils/load-state';

@Component({
  selector: 'app-accounts',
  imports: [DatePipe],
  templateUrl: './accounts.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accounts {
  private readonly clientService = inject(ClientService);
  private readonly auth = inject(AuthService);

  /** The logged-in client's own accounts (the API no longer lists other clients to a CLIENT). */
  protected readonly state = toSignal(
    toLoadState(this.clientService.getCurrentClient().pipe(map((client) => [client]))),
    { requireSync: true },
  );

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
