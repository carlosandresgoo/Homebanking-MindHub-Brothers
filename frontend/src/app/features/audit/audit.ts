import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { debounceTime, switchMap } from 'rxjs';

import { AuditService } from '../../core/api/audit.service';
import { SpanishPaginatorIntl } from '../../core/i18n/paginator-intl';
import {
  AUDIT_ACTION_LABEL,
  AuditAction,
  AuditEvent,
  AuditQuery,
} from '../../core/models/audit.model';
import { toLoadState } from '../../core/utils/load-state';

/** Local date (yyyy-MM-dd) to the ISO instant of its start, optionally shifted by days. */
function startOfDay(date: string, plusDays = 0): string {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(y, m - 1, d + plusDays).toISOString();
}

@Component({
  selector: 'app-audit',
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    ReactiveFormsModule,
  ],
  providers: [{ provide: MatPaginatorIntl, useClass: SpanishPaginatorIntl }],
  templateUrl: './audit.html',
  styleUrl: './audit.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Audit {
  private readonly auditService = inject(AuditService);

  protected readonly columns = ['occurredAt', 'actor', 'action', 'target', 'outcome', 'ip'];
  protected readonly actionLabel = AUDIT_ACTION_LABEL;
  protected readonly actions = Object.keys(AUDIT_ACTION_LABEL) as AuditAction[];

  protected readonly filters = inject(NonNullableFormBuilder).group({
    actor: [''],
    action: ['' as AuditAction | ''],
    from: [''],
    to: [''],
  });

  private readonly paging = signal({ page: 0, size: 20 });
  private readonly filterValue = signal(this.filters.getRawValue());

  protected readonly query = computed<AuditQuery>(() => {
    const f = this.filterValue();
    return {
      actor: f.actor?.trim() || undefined,
      action: f.action || undefined,
      from: f.from ? startOfDay(f.from) : undefined,
      // "Hasta" is inclusive for the user: up to the start of the next day.
      to: f.to ? startOfDay(f.to, 1) : undefined,
      ...this.paging(),
    };
  });

  protected readonly state = toSignal(
    toObservable(this.query).pipe(switchMap((q) => toLoadState(this.auditService.search(q)))),
    { initialValue: { status: 'loading' as const } },
  );

  constructor() {
    // Debounced filters; the new filters and the reset to the first page are applied together so
    // they produce a single request.
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      this.filterValue.set(this.filters.getRawValue());
      this.paging.update((p) => ({ ...p, page: 0 }));
    });
  }

  protected labelOf(event: AuditEvent): string {
    return AUDIT_ACTION_LABEL[event.action] ?? event.action;
  }

  protected onPage(event: PageEvent): void {
    this.paging.set({ page: event.pageIndex, size: event.pageSize });
  }

  protected clear(): void {
    this.filters.reset();
  }
}
