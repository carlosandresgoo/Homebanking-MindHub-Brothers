import { CurrencyPipe, DatePipe, UpperCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { AuthService } from '../../core/auth/auth.service';
import { AccountDetail } from '../../core/models/account.model';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';

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
    MatIconModule,
    MatProgressBarModule,
    RouterLink,
  ],
  templateUrl: './account-detail.html',
  styleUrl: './account-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountDetailPage {
  private readonly accountService = inject(AccountService);
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
