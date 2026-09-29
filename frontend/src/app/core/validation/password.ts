import { AbstractControl, ValidationErrors, Validators } from '@angular/forms';

/** Same limits as the backend (BCrypt ignores bytes past 72). */
export const PASSWORD_MIN = 12;
export const PASSWORD_MAX = 72;

export const newPasswordValidators = [
  Validators.required,
  Validators.minLength(PASSWORD_MIN),
  Validators.maxLength(PASSWORD_MAX),
];

/** Group validator: `newPassword` and `confirm` must match. */
export function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const value = group.value as { newPassword?: string; confirm?: string };
  return value.newPassword === value.confirm ? null : { mismatch: true };
}
