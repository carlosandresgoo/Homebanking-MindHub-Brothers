import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Logo mark + wordmark, linking home. `inverse` is for use on the primary gradient. */
@Component({
  selector: 'app-brand',
  imports: [RouterLink],
  template: `
    <a
      class="brand"
      [class.inverse]="inverse()"
      [class.compact]="compactOnMobile()"
      routerLink="/"
      aria-label="MindHub Brothers, inicio"
    >
      <span class="mark"><img src="assets/icon.png" alt="" width="28" height="28" /></span>
      <span class="name">MindHub <strong>Brothers</strong></span>
    </a>
  `,
  styles: `
    .brand {
      display: inline-flex;
      align-items: center;
      gap: 10px;
      color: var(--mat-sys-on-surface);
      text-decoration: none;
      font: var(--mat-sys-title-medium);
      letter-spacing: -0.01em;
    }
    .mark {
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      border-radius: 12px;
      background: var(--mat-sys-primary-container);
    }
    .name strong {
      color: var(--mat-sys-primary);
    }
    .inverse {
      color: #fff;
    }
    .inverse .mark {
      background: rgb(255 255 255 / 0.18);
    }
    .inverse .name strong {
      color: #fff;
    }
    @media (max-width: 600px) {
      .compact .name {
        display: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Brand {
  readonly inverse = input(false);
  /** Hides the wordmark on small screens (only the logo mark remains). */
  readonly compactOnMobile = input(false);
}
