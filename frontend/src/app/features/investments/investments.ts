import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { BehaviorSubject, filter, forkJoin, switchMap } from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { FixedTermService } from '../../core/api/fixed-term.service';
import { FixedTerm } from '../../core/models/fixed-term.model';
import { toLoadState } from '../../core/utils/load-state';
import { FixedTermDialog, FixedTermDialogData } from './fixed-term-dialog/fixed-term-dialog';

const DAY_MS = 24 * 60 * 60 * 1000;

function localDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

/** Fixed-term deposits: active ones with their progress, the history, and constituting a new one. */
@Component({
  selector: 'app-investments',
  imports: [
    CurrencyPipe,
    DatePipe,
    PercentPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatSlideToggleModule,
  ],
  templateUrl: './investments.html',
  styleUrl: './investments.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Investments {
  private readonly fixedTermService = inject(FixedTermService);
  private readonly accountService = inject(AccountService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly opening = signal(false);

  protected readonly state = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.fixedTermService.getMine()))),
    { requireSync: true },
  );

  private readonly all = computed(() => {
    const s = this.state();
    return s.status === 'loaded' ? s.data : [];
  });
  protected readonly active = computed(() => this.all().filter((f) => f.status === 'ACTIVE'));
  protected readonly paid = computed(() => this.all().filter((f) => f.status === 'PAID'));
  protected readonly invested = computed(() => this.active().reduce((s, f) => s + f.principal, 0));
  protected readonly toEarn = computed(() => this.active().reduce((s, f) => s + f.interest, 0));
  protected readonly earned = computed(() => this.paid().reduce((s, f) => s + f.interest, 0));

  /** Share of the term already elapsed (0..1), for the progress bar. */
  protected progress(term: FixedTerm): number {
    const start = localDate(term.startDate).getTime();
    const end = localDate(term.maturityDate).getTime();
    return Math.min(1, Math.max(0, (Date.now() - start) / (end - start || 1)));
  }

  protected daysLeft(term: FixedTerm): number {
    return Math.max(0, Math.ceil((localDate(term.maturityDate).getTime() - Date.now()) / DAY_MS));
  }

  protected retry(): void {
    this.reload$.next();
  }

  /** Loads fresh balances and the rates, then opens the simulator. */
  protected openNew(): void {
    this.opening.set(true);
    forkJoin({
      plans: this.fixedTermService.getPlans(),
      accounts: this.accountService.getMyAccounts(),
    })
      .pipe(
        switchMap((data: FixedTermDialogData) => {
          this.opening.set(false);
          return this.dialog
            .open<FixedTermDialog, FixedTermDialogData, FixedTerm>(FixedTermDialog, {
              data,
              maxWidth: '95vw',
            })
            .afterClosed();
        }),
        filter((created): created is FixedTerm => !!created),
      )
      .subscribe({
        next: (created) => {
          this.snackBar.open(
            `Constituiste un plazo fijo por $ ${created.principal.toLocaleString('es-AR')}.`,
            'OK',
          );
          this.reload$.next();
        },
        error: () => {
          this.opening.set(false);
          this.snackBar.open('No pudimos cargar las tasas. Intentá de nuevo.', 'OK');
        },
      });
  }

  protected toggleRenew(term: FixedTerm, event: MatSlideToggleChange): void {
    this.fixedTermService.setAutoRenew(term.id, event.checked).subscribe({
      next: () =>
        this.snackBar.open(
          event.checked
            ? 'Se va a renovar automáticamente al vencer.'
            : 'Al vencer te acreditamos todo en la cuenta.',
          'OK',
        ),
      error: () => {
        event.source.checked = !event.checked;
        this.snackBar.open('No pudimos cambiar la renovación. Intentá de nuevo.', 'OK');
      },
    });
  }
}
