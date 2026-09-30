import { CurrencyPipe, DatePipe, UpperCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import {
  BehaviorSubject,
  catchError,
  combineLatest,
  debounceTime,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { MovementService } from '../../core/api/movement.service';
import { AuthService } from '../../core/auth/auth.service';
import { SpanishPaginatorIntl } from '../../core/i18n/paginator-intl';
import {
  AccountDetail,
  CATEGORY_LABEL,
  MovementQuery,
  Transaction,
  TransactionCategory,
  TransactionType,
} from '../../core/models/account.model';
import { Page } from '../../core/models/page.model';
import { saveDownload } from '../../core/utils/download';
import { LoadState, toLoadState } from '../../core/utils/load-state';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { ReceiveCard } from './receive-card/receive-card';

type DetailState =
  | { status: 'loading' }
  | { status: 'loaded'; data: AccountDetail }
  | { status: 'not-found' }
  | { status: 'error' };

@Component({
  selector: 'app-account-detail',
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
    MatProgressSpinnerModule,
    MatSelectModule,
    ReactiveFormsModule,
    ReceiveCard,
    RouterLink,
  ],
  // Provided here (not app-wide) so the paginator stays in this lazy chunk.
  providers: [{ provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './account-detail.html',
  styleUrl: './account-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountDetailPage {
  private readonly accountService = inject(AccountService);
  private readonly movementService = inject(MovementService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  /** Route param `:id` (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly isClient = computed(() => this.auth.role() === 'CLIENT');
  protected readonly backLink = computed(() => (this.isClient() ? '/accounts' : '/manager'));
  protected readonly backLabel = computed(() => (this.isClient() ? 'Mis cuentas' : 'Clientes'));
  protected readonly closing = signal(false);

  protected readonly state = toSignal(
    combineLatest([toObservable(this.id), this.reload$]).pipe(
      switchMap(([id]) =>
        this.accountService.getAccount(Number(id)).pipe(
          map((data): DetailState => ({ status: 'loaded', data })),
          catchError((err: unknown) =>
            of<DetailState>(
              err instanceof HttpErrorResponse && err.status === 404
                ? { status: 'not-found' }
                : { status: 'error' },
            ),
          ),
          startWith<DetailState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as DetailState },
  );

  // --- Movements: filters, pages and export ---

  protected readonly categoryLabel = CATEGORY_LABEL;
  protected readonly categories = Object.keys(CATEGORY_LABEL) as TransactionCategory[];
  protected readonly filters = inject(NonNullableFormBuilder).group({
    from: [''],
    to: [''],
    type: ['' as TransactionType | ''],
    category: ['' as TransactionCategory | ''],
    q: ['', Validators.maxLength(50)],
  });
  private readonly filterValue = signal(this.filters.getRawValue());
  protected readonly paging = signal({ page: 0, size: 20 });
  protected readonly exporting = signal(false);

  protected readonly movementQuery = computed<MovementQuery>(() => {
    const f = this.filterValue();
    return {
      from: f.from || undefined,
      to: f.to || undefined,
      type: f.type || undefined,
      category: f.category || undefined,
      q: f.q.trim() || undefined,
    };
  });
  protected readonly hasFilters = computed(() =>
    Object.values(this.movementQuery()).some((v) => v !== undefined),
  );
  /** "Desde" after "Hasta": no request is sent until the user fixes it. */
  protected readonly invalidRange = computed(() => {
    const { from, to } = this.movementQuery();
    return !!from && !!to && from > to;
  });

  protected readonly movements = toSignal(
    toObservable(
      computed(() => ({
        id: Number(this.id()),
        query: this.movementQuery(),
        invalid: this.invalidRange(),
        ...this.paging(),
      })),
    ).pipe(
      switchMap((r) =>
        r.invalid
          ? of<LoadState<Page<Transaction>>>({ status: 'error' })
          : toLoadState(this.movementService.page(r.id, r.query, r.page, r.size)),
      ),
    ),
    { initialValue: { status: 'loading' } as LoadState<Page<Transaction>> },
  );

  constructor() {
    // Debounced filters; the reset to the first page goes with them (a single request).
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      if (this.filters.invalid) return;
      this.filterValue.set(this.filters.getRawValue());
      this.paging.update((p) => ({ ...p, page: 0 }));
    });
  }

  protected onPage(event: PageEvent): void {
    this.paging.set({ page: event.pageIndex, size: event.pageSize });
  }

  protected clearFilters(): void {
    this.filters.reset();
  }

  protected exportCsv(account: AccountDetail): void {
    this.exporting.set(true);
    this.movementService.exportCsv(account.id, this.movementQuery()).subscribe({
      next: (response) => {
        this.exporting.set(false);
        saveDownload(response, `movimientos-${account.number}.csv`);
      },
      error: () => {
        this.exporting.set(false);
        this.snackBar.open('No pudimos exportar los movimientos. Intentá de nuevo.', 'OK');
      },
    });
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected close(account: AccountDetail): void {
    const data: ConfirmDialogData = {
      title: `Cerrar la cuenta ${account.number.toUpperCase()}`,
      message:
        'La cuenta dejará de estar disponible para operar. Esta acción no se puede deshacer.',
      confirmLabel: 'Cerrar cuenta',
      icon: 'warning',
      danger: true,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(
        switchMap((confirmed) => {
          if (!confirmed) return of(false);
          this.closing.set(true);
          return this.accountService.closeAccount(account.id).pipe(
            map(() => true),
            catchError(() => {
              this.closing.set(false);
              this.snackBar.open('No pudimos cerrar la cuenta. Intentá de nuevo.', 'OK');
              return of(false);
            }),
          );
        }),
      )
      .subscribe((closed) => {
        if (closed) {
          this.snackBar.open(`Cerraste la cuenta ${account.number.toUpperCase()}.`, 'OK');
          void this.router.navigateByUrl('/accounts');
        }
      });
  }
}
