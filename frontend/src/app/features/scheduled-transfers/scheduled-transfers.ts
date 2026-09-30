import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, Observable, catchError, filter, of, switchMap } from 'rxjs';

import { AccountService } from '../../core/api/account.service';
import { ScheduledTransferService } from '../../core/api/scheduled-transfer.service';
import { Account } from '../../core/models/account.model';
import {
  FREQUENCY_LABEL,
  MAX_OPEN_SCHEDULED,
  SCHEDULED_STATUS_LABEL,
  ScheduledTransfer,
} from '../../core/models/scheduled-transfer.model';
import { toLoadState } from '../../core/utils/load-state';
import { ConfirmDialog, ConfirmDialogData } from '../../shared/confirm-dialog/confirm-dialog';
import { ScheduleDialog, ScheduleDialogData } from './schedule-dialog/schedule-dialog';

/** Scheduled transfers: create, pause/resume, cancel, and the outcome of the last run. */
@Component({
  selector: 'app-scheduled-transfers',
  imports: [
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    RouterLink,
  ],
  template: `
    @let s = state();
    @if (s.status === 'loading' || working()) {
      <mat-progress-bar mode="indeterminate" aria-label="Cargando" />
    }

    <div class="hb-page narrow">
      <a mat-button class="back" routerLink="/transfers">
        <mat-icon>arrow_back</mat-icon>
        Transferir
      </a>
      <header class="hb-page-header">
        <div>
          <h1>Transferencias programadas</h1>
          <p>Pagos que se repiten o que querés dejar listos para una fecha.</p>
        </div>
        <button
          mat-flat-button
          type="button"
          class="add-button"
          (click)="add()"
          [disabled]="accounts().length === 0 || openCount() >= maxOpen"
        >
          <mat-icon>add</mat-icon>
          Programar
        </button>
      </header>

      @switch (s.status) {
        @case ('error') {
          <div class="state-card" role="alert">
            <mat-icon class="state-icon error">cloud_off</mat-icon>
            <h2>No pudimos cargar tus transferencias programadas</h2>
            <button mat-stroked-button type="button" (click)="reload()">Reintentar</button>
          </div>
        }
        @case ('loaded') {
          @if (s.data.length === 0) {
            <div class="state-card">
              <mat-icon class="state-icon">event_repeat</mat-icon>
              <h2>No tenés transferencias programadas</h2>
              <p>
                Programá el alquiler, una cuota o un ahorro mensual y olvidate de hacerlo a mano.
              </p>
            </div>
          } @else {
            <ul class="list" aria-label="Transferencias programadas">
              @for (item of s.data; track item.id) {
                <li
                  class="item"
                  [class.closed]="item.status === 'FINISHED' || item.status === 'CANCELLED'"
                >
                  <div class="main">
                    <strong class="hb-tabular">{{ item.amount | currency }}</strong>
                    <span
                      >a {{ item.targetHolder }} ·
                      <span class="hb-tabular">{{ item.targetAccountNumber }}</span></span
                    >
                    <span class="meta">
                      {{ frequencyLabel[item.frequency] }} desde {{ item.sourceAccountNumber }}
                      @if (item.description) {
                        · {{ item.description }}
                      }
                    </span>
                    @if (item.nextRun) {
                      <span class="meta"
                        >Próxima: {{ item.nextRun | date: 'longDate' }}
                        @if (item.maxRuns && item.frequency !== 'ONCE') {
                          ({{ item.runs + 1 }} de {{ item.maxRuns }})
                        }
                      </span>
                    }
                    @if (item.lastOutcome === 'FAILED') {
                      <span class="failed" role="note">
                        <mat-icon>error</mat-icon>
                        La última ({{ item.lastRunAt | date: 'shortDate' }}) no se pudo hacer:
                        {{ item.lastError }}.
                      </span>
                    }
                  </div>
                  <div class="side">
                    <span class="status" [attr.data-status]="item.status">{{
                      statusLabel[item.status]
                    }}</span>
                    @if (item.status === 'ACTIVE' || item.status === 'PAUSED') {
                      <span class="actions">
                        @if (item.frequency !== 'ONCE') {
                          @if (item.status === 'ACTIVE') {
                            <button
                              mat-icon-button
                              type="button"
                              class="pause"
                              (click)="pause(item)"
                              [attr.aria-label]="'Pausar la transferencia a ' + item.targetHolder"
                              matTooltip="Pausar"
                            >
                              <mat-icon>pause</mat-icon>
                            </button>
                          } @else {
                            <button
                              mat-icon-button
                              type="button"
                              class="resume"
                              (click)="resume(item)"
                              [attr.aria-label]="'Reanudar la transferencia a ' + item.targetHolder"
                              matTooltip="Reanudar"
                            >
                              <mat-icon>play_arrow</mat-icon>
                            </button>
                          }
                        }
                        <button
                          mat-icon-button
                          type="button"
                          class="cancel"
                          (click)="cancel(item)"
                          [attr.aria-label]="'Cancelar la transferencia a ' + item.targetHolder"
                          matTooltip="Cancelar"
                        >
                          <mat-icon>event_busy</mat-icon>
                        </button>
                      </span>
                    }
                  </div>
                </li>
              }
            </ul>
          }
        }
      }
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    mat-progress-bar {
      position: fixed;
      top: 68px;
      left: 0;
      right: 0;
      z-index: 5;
    }
    .back {
      margin: -8px 0 16px -12px;
    }
    .list {
      display: grid;
      gap: 12px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .item {
      display: flex;
      justify-content: space-between;
      gap: 16px;
      padding: 16px 20px;
      border-radius: var(--hb-radius);
      background: var(--mat-sys-surface);
      border: 1px solid var(--mat-sys-outline-variant);
    }
    .item.closed {
      opacity: 0.7;
    }
    .main {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }
    .main strong {
      font: var(--mat-sys-title-medium);
    }
    .meta {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .failed {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-top: 6px;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }
    .failed mat-icon {
      width: 18px;
      height: 18px;
      font-size: 18px;
    }
    .side {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;
    }
    .status {
      padding: 2px 10px;
      border-radius: 999px;
      background: var(--mat-sys-surface-container-high);
      font: var(--mat-sys-label-medium);
    }
    .status[data-status='ACTIVE'] {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .status[data-status='PAUSED'] {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .actions {
      display: flex;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScheduledTransfers {
  private readonly service = inject(ScheduledTransferService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly frequencyLabel = FREQUENCY_LABEL;
  protected readonly statusLabel = SCHEDULED_STATUS_LABEL;
  protected readonly maxOpen = MAX_OPEN_SCHEDULED;
  protected readonly working = signal(false);

  protected readonly state = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.service.getMine()))),
    { requireSync: true },
  );
  protected readonly accounts = toSignal(
    inject(AccountService)
      .getMyAccounts()
      .pipe(catchError(() => of<Account[]>([]))),
    { initialValue: [] as Account[] },
  );
  protected readonly openCount = computed(() => {
    const s = this.state();
    return s.status === 'loaded'
      ? s.data.filter((t) => t.status === 'ACTIVE' || t.status === 'PAUSED').length
      : 0;
  });

  protected reload(): void {
    this.reload$.next();
  }

  protected add(): void {
    this.dialog
      .open<ScheduleDialog, ScheduleDialogData, ScheduledTransfer>(ScheduleDialog, {
        data: { accounts: this.accounts() },
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((created): created is ScheduledTransfer => !!created))
      .subscribe(() => {
        this.snackBar.open('Programaste la transferencia.', 'OK');
        this.reload$.next();
      });
  }

  protected pause(item: ScheduledTransfer): void {
    this.act(this.service.pause(item.id), 'La pausaste. No se va a hacer hasta que la reanudes.');
  }

  protected resume(item: ScheduledTransfer): void {
    this.act(
      this.service.resume(item.id),
      'La reanudaste. Las fechas que pasaron mientras estaba pausada no se hacen.',
    );
  }

  protected cancel(item: ScheduledTransfer): void {
    const data: ConfirmDialogData = {
      title: 'Cancelar la transferencia programada',
      message: `No se van a hacer más transferencias a ${item.targetHolder} por esta programación.`,
      confirmLabel: 'Cancelar programación',
      icon: 'event_busy',
      danger: true,
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(filter(Boolean))
      .subscribe(() =>
        this.act(this.service.cancel(item.id), 'Cancelaste la transferencia programada.'),
      );
  }

  private act(request: Observable<ScheduledTransfer>, message: string): void {
    this.working.set(true);
    request.subscribe({
      next: () => {
        this.working.set(false);
        this.snackBar.open(message, 'OK');
        this.reload$.next();
      },
      error: () => {
        this.working.set(false);
        this.snackBar.open('No pudimos actualizarla. Intentá de nuevo.', 'OK');
        this.reload$.next();
      },
    });
  }
}
