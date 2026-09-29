import { CurrencyPipe, DatePipe, UpperCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { BehaviorSubject, switchMap } from 'rxjs';

import { ClientService } from '../../core/api/client.service';
import { toLoadState } from '../../core/utils/load-state';

@Component({
  selector: 'app-accounts',
  imports: [
    CurrencyPipe,
    DatePipe,
    UpperCasePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accounts {
  private readonly clientService = inject(ClientService);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  /** The logged-in client's own data (the API never lists other clients to a CLIENT). */
  protected readonly state = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.clientService.getCurrentClient()))),
    { requireSync: true },
  );

  protected readonly totalBalance = computed(() => {
    const s = this.state();
    return s.status === 'loaded'
      ? s.data.accounts.reduce((sum, account) => sum + account.balance, 0)
      : 0;
  });

  protected retry(): void {
    this.reload$.next();
  }
}
