import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { filter } from 'rxjs';

import { Account } from '../../../core/models/account.model';
import { formatCbu, shareText } from '../../../core/utils/cbu';
import { AliasDialog, AliasDialogData } from '../alias-dialog/alias-dialog';

/** CBU, alias and number of an account, to copy or share; the owner can change the alias. */
@Component({
  selector: 'app-receive-card',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    @let a = account();
    <section class="receive" aria-labelledby="receive-title">
      <header>
        <h2 id="receive-title">
          <mat-icon>move_to_inbox</mat-icon>
          Datos para recibir dinero
        </h2>
        <button mat-button type="button" class="share" (click)="share()">
          <mat-icon>share</mat-icon>
          Compartir
        </button>
      </header>
      <dl>
        <div>
          <dt>CBU</dt>
          <dd class="hb-tabular cbu">{{ formatCbu(a.cbu) }}</dd>
          <span class="row-actions">
            <button
              mat-icon-button
              type="button"
              class="copy-cbu"
              aria-label="Copiar CBU"
              matTooltip="Copiar CBU"
              (click)="copy(a.cbu, 'el CBU')"
            >
              <mat-icon>content_copy</mat-icon>
            </button>
          </span>
        </div>
        <div>
          <dt>Alias</dt>
          <dd class="alias">{{ a.alias }}</dd>
          <span class="row-actions">
            <button
              mat-icon-button
              type="button"
              class="copy-alias"
              aria-label="Copiar alias"
              matTooltip="Copiar alias"
              (click)="copy(a.alias, 'el alias')"
            >
              <mat-icon>content_copy</mat-icon>
            </button>
            @if (editable()) {
              <button
                mat-icon-button
                type="button"
                class="edit-alias"
                aria-label="Cambiar alias"
                matTooltip="Cambiar alias"
                (click)="editAlias()"
              >
                <mat-icon>edit</mat-icon>
              </button>
            }
          </span>
        </div>
        <div>
          <dt>N.º de cuenta</dt>
          <dd class="hb-tabular">{{ a.number.toUpperCase() }}</dd>
        </div>
      </dl>
    </section>
  `,
  styles: `
    .receive {
      padding: 20px 24px;
      border-radius: 20px;
      background: var(--mat-sys-surface-container-low);
      border: 1px solid var(--mat-sys-outline-variant);
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 8px;
    }
    h2 {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
      font: var(--mat-sys-title-medium);
    }
    h2 mat-icon {
      color: var(--mat-sys-primary);
    }
    dl {
      margin: 0;
    }
    dl > div {
      display: grid;
      grid-template-columns: 120px 1fr auto;
      align-items: center;
      gap: 8px;
      min-height: 48px;
      border-top: 1px solid var(--mat-sys-outline-variant);
    }
    dt {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
    }
    dd {
      margin: 0;
      font: var(--mat-sys-body-large);
      overflow-wrap: anywhere;
    }
    .alias {
      font-weight: 600;
    }
    .row-actions {
      display: flex;
    }
    @media (max-width: 600px) {
      dl > div {
        grid-template-columns: 1fr auto;
        padding: 6px 0;
      }
      dt {
        grid-column: 1 / -1;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReceiveCard {
  private readonly clipboard = inject(Clipboard);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly account = input.required<Pick<Account, 'id' | 'number' | 'cbu' | 'alias'>>();
  /** Only the owner (a CLIENT) can change the alias. */
  readonly editable = input(false);
  /** Emits the account with its new alias. */
  readonly aliasChanged = output<Account>();

  protected readonly formatCbu = formatCbu;

  protected copy(text: string, what: string): void {
    this.snackBar.open(
      this.clipboard.copy(text) ? `Copiaste ${what}.` : `No pudimos copiar ${what}.`,
      'OK',
      { duration: 3000 },
    );
  }

  /** The system share sheet when available (phones), otherwise the clipboard. */
  protected share(): void {
    const text = shareText(this.account());
    if (typeof navigator.share === 'function') {
      navigator.share({ title: 'Mis datos para transferirme', text }).catch(() => undefined);
      return;
    }
    this.copy(text, 'tus datos para recibir transferencias');
  }

  protected editAlias(): void {
    this.dialog
      .open<AliasDialog, AliasDialogData, Account>(AliasDialog, {
        data: { account: this.account() },
        maxWidth: '95vw',
      })
      .afterClosed()
      .pipe(filter((saved): saved is Account => !!saved))
      .subscribe((saved) => {
        this.snackBar.open(`Tu nuevo alias es ${saved.alias}.`, 'OK', { duration: 4000 });
        this.aliasChanged.emit(saved);
      });
  }
}
