import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { compactMoney, niceScale, observeWidth } from './chart-utils';

export interface LinePoint {
  /** `yyyy-MM-dd`. */
  date: string;
  value: number;
}

const HEIGHT = 200;
const MARGIN = { top: 12, right: 12, bottom: 28, left: 64 };

/** A value over time (e.g. the daily balance) as a line with a soft area underneath. */
@Component({
  selector: 'app-line-chart',
  imports: [CurrencyPipe, DatePipe],
  template: `
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
      @if (paths(); as p) {
        <path class="area" [attr.d]="p.area" />
        <path class="line" [attr.d]="p.line" />
        <circle class="last" [attr.cx]="p.lastX" [attr.cy]="p.lastY" r="4" />
      }
      @if (points().length > 0) {
        <text class="axis" [attr.x]="margin.left" [attr.y]="height - 8">
          {{ points()[0].date | date: 'd MMM' }}
        </text>
        <text
          class="axis"
          [attr.x]="width() - margin.right"
          [attr.y]="height - 8"
          text-anchor="end"
        >
          {{ points()[points().length - 1].date | date: 'd MMM' }}
        </text>
      }
    </svg>
    @if (points().length > 0) {
      <p class="hb-visually-hidden">
        {{ label() }}: de {{ points()[0].value | currency }} el
        {{ points()[0].date | date: 'longDate' }} a
        {{ points()[points().length - 1].value | currency }} el
        {{ points()[points().length - 1].date | date: 'longDate' }}.
      </p>
    }
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
    }
    .axis {
      fill: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .line {
      fill: none;
      stroke: var(--mat-sys-primary);
      stroke-width: 2.5;
      stroke-linejoin: round;
    }
    .area {
      fill: color-mix(in srgb, var(--mat-sys-primary) 14%, transparent);
    }
    .last {
      fill: var(--mat-sys-primary);
      stroke: var(--mat-sys-surface);
      stroke-width: 2;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LineChart {
  readonly points = input.required<LinePoint[]>();
  /** Accessible description of the chart. */
  readonly label = input.required<string>();

  protected readonly height = HEIGHT;
  protected readonly margin = MARGIN;
  protected readonly width = observeWidth();

  private readonly plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
  /** The axis starts at 0 unless the balance went negative. */
  private readonly range = computed(() => {
    const values = this.points().map((p) => p.value);
    const min = Math.min(0, ...values);
    const { max, step } = niceScale(Math.max(...values, 0) - min);
    return { min, max: min + max, step };
  });

  private x(index: number): number {
    const count = Math.max(1, this.points().length - 1);
    return MARGIN.left + ((this.width() - MARGIN.left - MARGIN.right) * index) / count;
  }

  private y(value: number): number {
    const { min, max } = this.range();
    return MARGIN.top + this.plotHeight * (1 - (value - min) / (max - min || 1));
  }

  protected readonly ticks = computed(() => {
    const { min, max, step } = this.range();
    const ticks = [];
    for (let value = min; value <= max + step / 2; value += step) {
      ticks.push({ value, y: this.y(value), label: compactMoney(value) });
    }
    return ticks;
  });

  protected readonly paths = computed(() => {
    const points = this.points();
    if (points.length === 0) return null;
    const coords = points.map((p, i) => [this.x(i), this.y(p.value)] as const);
    const line = coords
      .map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`)
      .join('');
    const baseline = this.y(Math.max(this.range().min, 0));
    const [firstX] = coords[0];
    const [lastX, lastY] = coords[coords.length - 1];
    return {
      line,
      area: `${line}L${lastX.toFixed(1)},${baseline}L${firstX.toFixed(1)},${baseline}Z`,
      lastX,
      lastY,
    };
  });
}
