import { CurrencyPipe, DatePipe, UpperCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, switchMap } from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { ClientService } from '../../core/api/client.service';
import { MAX_ACTIVE_ACCOUNTS } from '../../core/models/account.model';
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
    RouterLink,
  ],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Accounts {
  private readonly clientService = inject(ClientService);
  private readonly accountService = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly maxAccounts = MAX_ACTIVE_ACCOUNTS;
  protected readonly opening = signal(false);

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

  protected openAccount(): void {
    this.opening.set(true);
    this.accountService.openAccount().subscribe({
      next: (account) => {
        this.opening.set(false);
        this.snackBar.open(`Abriste la cuenta ${account.number}.`, 'OK');
        this.reload$.next();
      },
      error: (err: unknown) => {
        this.opening.set(false);
        const limit = err instanceof HttpErrorResponse && err.status === 409;
        this.snackBar.open(
          limit
            ? `Ya tenés el máximo de ${MAX_ACTIVE_ACCOUNTS} cuentas activas.`
            : 'No pudimos abrir la cuenta. Intentá de nuevo.',
          'OK',
        );
      },
    });
  }
}
