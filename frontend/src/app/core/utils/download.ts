import { HttpResponse } from '@angular/common/http';

/** Saves a downloaded file, named after its Content-Disposition (or `fallbackName`). */
export function saveDownload(response: HttpResponse<Blob>, fallbackName: string): void {
  if (!response.body) return;
  const url = URL.createObjectURL(response.body);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileNameFrom(response.headers.get('Content-Disposition')) ?? fallbackName;
  link.click();
  // Give the browser a moment to start the download before releasing the blob.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** `attachment; filename="a.csv"` → `a.csv`. Path separators are dropped. */
export function fileNameFrom(disposition: string | null): string | null {
  const match = disposition?.match(/filename="?([^";]+)"?/i);
  return match ? match[1].replace(/[\\/]/g, '_') : null;
}
