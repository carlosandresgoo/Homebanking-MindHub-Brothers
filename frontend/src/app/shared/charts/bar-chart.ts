import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { compactMoney, niceScale, observeWidth } from './chart-utils';

export interface BarSeries {
  name: string;
  /** Any CSS color, typically a theme token: `var(--hb-positive)`. */
  color: string;
}

export interface BarGroup {
  label: string;
  /** One value per series, in the same order. */
  values: number[];
}

const HEIGHT = 240;
const MARGIN = { top: 12, right: 8, bottom: 28, left: 64 };

/** Grouped vertical bars (e.g. income vs expenses per month) with a money axis. */
@Component({
  selector: 'app-bar-chart',
  imports: [CurrencyPipe],
  template: `
    <div class="legend" aria-hidden="true">
      @for (s of series(); track s.name) {
        <span><i [style.background]="s.color"></i>{{ s.name }}</span>
      }
    </div>
    <svg [attr.width]="width()" [attr.height]="height" role="img" [attr.aria-label]="label()">
      @for (tick of ticks(); track tick.value) {
        <line
          class="grid"
          [attr.x1]="margin.left"
          [attr.x2]="width() - margin.right"
          [attr.y1]="tick.y"
          [attr.y2]="tick.y"
        />
        <text
          class="axis"
          [attr.x]="margin.left - 8"
          [attr.y]="tick.y"
          text-anchor="end"
          dominant-baseline="middle"
        >
          {{ tick.label }}
        </text>
      }
      @for (bar of bars(); track bar.key) {
        <rect
          [attr.x]="bar.x"
          [attr.y]="bar.y"
          [attr.width]="bar.w"
          [attr.height]="bar.h"
          [attr.fill]="bar.color"
          rx="3"
        >
          <title>{{ bar.title }}</title>
        </rect>
      }
      @for (group of groupLabels(); track group.label) {
        <text class="axis" [attr.x]="group.x" [attr.y]="height - 8" text-anchor="middle">
          {{ group.label }}
        </text>
      }
    </svg>
    <!-- Wrapped: a table ignores the 1px width and would widen the page. -->
    <div class="hb-visually-hidden">
      <table>
        <caption>
          {{
            label()
          }}
        </caption>
        <tr>
          <th scope="col">Período</th>
          @for (s of series(); track s.name) {
            <th scope="col">{{ s.name }}</th>
          }
        </tr>
        @for (group of groups(); track group.label) {
          <tr>
            <th scope="row">{{ group.label }}</th>
            @for (value of group.values; track $index) {
              <td>{{ value | currency }}</td>
            }
          </tr>
        }
      </table>
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
    }
    svg {
      display: block;
      overflow: visible;
    }
    .grid {
      stroke: var(--mat-sys-outline-variant);
      stroke-width: 1;
    }
    .axis {
      fill: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin-bottom: 8px;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
    }
    .legend span {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .legend i {
      width: 12px;
      height: 12px;
      border-radius: 3px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarChart {
  readonly series = input.required<BarSeries[]>();
  readonly groups = input.required<BarGroup[]>();
  /** Accessible description of the chart. */
  readonly label = input.required<string>();

  protected readonly height = HEIGHT;
  protected readonly margin = MARGIN;
  protected readonly width = observeWidth();

  private readonly scale = computed(() =>
    niceScale(Math.max(0, ...this.groups().flatMap((g) => g.values))),
  );
  private readonly plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
  private readonly groupWidth = computed(
    () => (this.width() - MARGIN.left - MARGIN.right) / Math.max(1, this.groups().length),
  );

  private y(value: number): number {
    return MARGIN.top + this.plotHeight * (1 - value / this.scale().max);
  }

  protected readonly ticks = computed(() => {
    const { max, step } = this.scale();
    const ticks = [];
    for (let value = 0; value <= max; value += step) {
      ticks.push({ value, y: this.y(value), label: compactMoney(value) });
    }
    return ticks;
  });

  protected readonly bars = computed(() => {
    const series = this.series();
    const groupWidth = this.groupWidth();
    const gap = 4;
    const barWidth = Math.max(4, Math.min(24, (groupWidth * 0.6 - gap) / series.length));
    const clusterWidth = barWidth * series.length + gap * (series.length - 1);
    return this.groups().flatMap((group, g) =>
      group.values.map((value, s) => {
        const x =
          MARGIN.left + g * groupWidth + (groupWidth - clusterWidth) / 2 + s * (barWidth + gap);
        const y = this.y(value);
        return {
          key: `${g}-${s}`,
          x,
          y,
          w: barWidth,
          h: Math.max(0, MARGIN.top + this.plotHeight - y),
          color: series[s].color,
          title: `${group.label} · ${series[s].name}: ${compactMoney(value)}`,
        };
      }),
    );
  });

  protected readonly groupLabels = computed(() =>
    this.groups().map((group, g) => ({
      label: group.label,
      x: MARGIN.left + g * this.groupWidth() + this.groupWidth() / 2,
    })),
  );
}
