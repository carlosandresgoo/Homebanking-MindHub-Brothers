import { DestroyRef, ElementRef, afterNextRender, inject, signal } from '@angular/core';

/**
 * Width of the host element in CSS pixels, kept up to date with a ResizeObserver. Charts draw at
 * their real size (instead of scaling a fixed viewBox) so labels keep a readable font size.
 */
export function observeWidth(initial = 600) {
  const width = signal(initial);
  const host = inject<ElementRef<HTMLElement>>(ElementRef);
  const destroyRef = inject(DestroyRef);
  afterNextRender(() => {
    const update = () => width.set(Math.max(200, Math.round(host.nativeElement.clientWidth)));
    update();
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(update);
    observer.observe(host.nativeElement);
    destroyRef.onDestroy(() => observer.disconnect());
  });
  return width.asReadonly();
}

/**
 * A round step for an axis (at most `intervals` of them) and the first multiple of it that covers
 * the data: 7_320 → { max: 8_000, step: 2_000 }; 41_000 → { max: 60_000, step: 20_000 }.
 */
export function niceScale(maxValue: number, intervals = 4): { max: number; step: number } {
  if (maxValue <= 0) return { max: intervals, step: 1 };
  const rough = maxValue / intervals;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const step =
    [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= rough) ?? 10 * magnitude;
  return { max: Math.ceil(maxValue / step) * step, step };
}

const compactArs = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  notation: 'compact',
  maximumFractionDigits: 1,
});

/** Short money labels for axes: "$ 12,5 mil". */
export function compactMoney(value: number): string {
  return compactArs.format(value);
}
