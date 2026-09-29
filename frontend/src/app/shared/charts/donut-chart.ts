import { CurrencyPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface DonutSlice {
  label: string;
  value: number;
  /** Any CSS color, typically a theme token. */
  color: string;
}

const SIZE = 180;
const STROKE = 26;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** Share of a total (e.g. spending by category) with a legend showing amounts and percentages. */
@Component({
  selector: 'app-donut-chart',
  imports: [CurrencyPipe, PercentPipe],
  template: `
    <svg
      [attr.viewBox]="'0 0 ' + size + ' ' + size"
      [attr.width]="size"
      [attr.height]="size"
      role="img"
      [attr.aria-label]="label()"
    >
      <circle
        class="track"
        [attr.cx]="size / 2"
        [attr.cy]="size / 2"
        [attr.r]="radius"
        [attr.stroke-width]="stroke"
      />
      @for (arc of arcs(); track arc.label) {
        <circle
          [attr.cx]="size / 2"
          [attr.cy]="size / 2"
          [attr.r]="radius"
          fill="none"
          [attr.stroke]="arc.color"
          [attr.stroke-width]="stroke"
          [attr.stroke-dasharray]="arc.dash"
          [attr.stroke-dashoffset]="arc.offset"
          [attr.transform]="'rotate(-90 ' + size / 2 + ' ' + size / 2 + ')'"
        >
          <title>{{ arc.label }}: {{ arc.value | currency }}</title>
        </circle>
      }
      <text class="total-label" [attr.x]="size / 2" [attr.y]="size / 2 - 10" text-anchor="middle">
        {{ centerLabel() }}
      </text>
      <text class="total" [attr.x]="size / 2" [attr.y]="size / 2 + 14" text-anchor="middle">
        {{ total() | currency: 'ARS' : 'symbol' : '1.0-0' }}
      </text>
    </svg>
    <ul class="legend">
      @for (arc of arcs(); track arc.label) {
        <li>
          <i [style.background]="arc.color" aria-hidden="true"></i>
          <span class="name">{{ arc.label }}</span>
          <span class="amount hb-tabular">{{ arc.value | currency }}</span>
          <span class="share hb-tabular">{{ arc.share | percent: '1.0-0' }}</span>
        </li>
      }
    </ul>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: center;
      gap: 24px;
    }
    svg {
      flex: none;
    }
    .track {
      fill: none;
      stroke: var(--mat-sys-surface-container-high);
    }
    .total-label {
      fill: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .total {
      fill: var(--mat-sys-on-surface);
      font: var(--mat-sys-title-medium);
      font-weight: 700;
    }
    .legend {
      display: grid;
      flex: 1;
      gap: 10px;
      min-width: 220px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    li {
      display: grid;
      grid-template-columns: 12px 1fr auto auto;
      align-items: center;
      gap: 10px;
      font: var(--mat-sys-body-medium);
    }
    i {
      width: 12px;
      height: 12px;
      border-radius: 3px;
    }
    .amount {
      font-weight: 600;
    }
    .share {
      min-width: 3ch;
      color: var(--mat-sys-on-surface-variant);
      text-align: right;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DonutChart {
  readonly slices = input.required<DonutSlice[]>();
  /** Accessible description of the chart. */
  readonly label = input.required<string>();
  readonly centerLabel = input('Total');

  protected readonly size = SIZE;
  protected readonly stroke = STROKE;
  protected readonly radius = RADIUS;

  protected readonly total = computed(() =>
    this.slices().reduce((sum, slice) => sum + Math.max(0, slice.value), 0),
  );

  protected readonly arcs = computed(() => {
    const total = this.total() || 1;
    let offset = 0;
    return this.slices()
      .filter((slice) => slice.value > 0)
      .map((slice) => {
        const length = (slice.value / total) * CIRCUMFERENCE;
        const arc = {
          ...slice,
          share: slice.value / total,
          dash: `${length} ${CIRCUMFERENCE - length}`,
          offset: -offset,
        };
        offset += length;
        return arc;
      });
  });
}
