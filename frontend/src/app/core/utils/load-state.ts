import { Observable, catchError, map, of, startWith } from 'rxjs';

export type LoadState<T> =
  | { status: 'loading' }
  | { status: 'loaded'; data: T }
  | { status: 'error' };

/** Wraps a one-shot request so templates can branch on loading / loaded / error. */
export function toLoadState<T>(source: Observable<T>): Observable<LoadState<T>> {
  return source.pipe(
    map((data): LoadState<T> => ({ status: 'loaded', data })),
    catchError(() => of<LoadState<T>>({ status: 'error' })),
    startWith<LoadState<T>>({ status: 'loading' }),
  );
}
