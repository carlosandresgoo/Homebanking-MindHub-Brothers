import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { MovementService } from '../../core/api/movement.service';
import { CATEGORY_LABEL, MovementReceipt } from '../../core/models/account.model';
import { Brand } from '../../shared/brand/brand';

type ReceiptState =
  | { status: 'loading' }
  | { status: 'loaded'; data: MovementReceipt }
  | { status: 'not-found' }
  | { status: 'error' };

/** Printable receipt of one movement ("Imprimir" also saves it as PDF from the browser). */
@Component({
  selector: 'app-receipt',
  imports: [Brand, CurrencyPipe, DatePipe, MatButtonModule, MatIconModule, RouterLink],
  templateUrl: './receipt.html',
  styleUrl: './receipt.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Receipt {
  private readonly movementService = inject(MovementService);

  /** Route param `:id`. */
  readonly id = input.required<string>();

  protected readonly categoryLabel = CATEGORY_LABEL;

  protected readonly state = toSignal(
    toObservable(this.id).pipe(
      switchMap((id) =>
        this.movementService.receipt(Number(id)).pipe(
          map((data): ReceiptState => ({ status: 'loaded', data })),
          catchError((err: unknown) =>
            of<ReceiptState>(
              err instanceof HttpErrorResponse && err.status === 404
                ? { status: 'not-found' }
                : { status: 'error' },
            ),
          ),
          startWith<ReceiptState>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as ReceiptState },
  );

  protected print(): void {
    window.print();
  }
}
