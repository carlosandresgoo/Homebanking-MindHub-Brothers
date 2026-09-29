import { Validators } from '@angular/forms';

/** Same limits as the backend (BCrypt ignores bytes past 72). */
export const PASSWORD_MIN = 12;
export const PASSWORD_MAX = 72;

export const newPasswordValidators = [
  Validators.required,
  Validators.minLength(PASSWORD_MIN),
  Validators.maxLength(PASSWORD_MAX),
];
