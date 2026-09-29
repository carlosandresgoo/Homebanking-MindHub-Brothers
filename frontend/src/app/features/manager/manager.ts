import { CurrencyPipe, DatePipe, UpperCasePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { BehaviorSubject, switchMap } from 'rxjs';

import { ClientService } from '../../core/api/client.service';
import { Client } from '../../core/models/client.model';
import { toLoadState } from '../../core/utils/load-state';
import { initials } from '../../shared/initials';
import { NewClientDialog } from './new-client-dialog/new-client-dialog';

/** Table row: the client plus derived, sortable columns. */
export interface ClientRow extends Client {
  fullName: string;
  initials: string;
  accountCount: number;
  totalBalance: number;
}

@Component({
  selector: 'app-manager',
  imports: [
    CurrencyPipe,
    DatePipe,
    UpperCasePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSortModule,
    MatTableModule,
  ],
  templateUrl: './manager.html',
  styleUrl: './manager.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Manager {
  private readonly clientService = inject(ClientService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly columns = [
    'client',
    'role',
    'accountCount',
    'totalBalance',
    'expand',
  ] as const;
  protected readonly dataSource = new MatTableDataSource<ClientRow>([]);
  protected readonly expandedId = signal<number | null>(null);

  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);

  protected readonly state = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.clientService.getClients()))),
    { requireSync: true },
  );

  private readonly rows = computed<ClientRow[]>(() => {
    const s = this.state();
    return s.status === 'loaded' ? s.data.map(toRow) : [];
  });

  protected readonly stats = computed(() => {
    const rows = this.rows();
    return {
      clients: rows.filter((row) => row.role === 'CLIENT').length,
      accounts: rows.reduce((sum, row) => sum + row.accountCount, 0),
      balance: rows.reduce((sum, row) => sum + row.totalBalance, 0),
    };
  });

  constructor() {
    this.dataSource.filterPredicate = (row, filter) =>
      `${row.fullName} ${row.email}`.toLowerCase().includes(filter);
    effect(() => {
      this.dataSource.data = this.rows();
    });
    // Sort and paginator only exist once the table is rendered (after the first load).
    effect(() => {
      this.dataSource.sort = this.sort() ?? null;
      this.dataSource.paginator = this.paginator() ?? null;
    });
  }

  protected applyFilter(value: string): void {
    this.dataSource.filter = value.trim().toLowerCase();
    this.dataSource.paginator?.firstPage();
  }

  protected toggle(row: ClientRow): void {
    this.expandedId.update((id) => (id === row.id ? null : row.id));
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected openNewClient(): void {
    this.dialog
      .open<NewClientDialog, void, Client>(NewClientDialog, { width: '520px', maxWidth: '95vw' })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.snackBar.open(`Cliente ${created.name} ${created.lastName} creado.`, 'OK');
          this.reload$.next();
        }
      });
  }
}

function toRow(client: Client): ClientRow {
  return {
    ...client,
    fullName: `${client.name} ${client.lastName}`,
    initials: initials(client.name, client.lastName),
    accountCount: client.accounts.length,
    totalBalance: client.accounts.reduce((sum, account) => sum + account.balance, 0),
  };
}
