/** Mirrors backend `PageDTO`: one page of a paginated result. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
