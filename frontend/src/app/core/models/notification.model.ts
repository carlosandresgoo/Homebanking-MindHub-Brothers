/** Mirrors backend `Notification.Type`. */
export type NotificationType =
  | 'WELCOME'
  | 'TRANSFER_RECEIVED'
  | 'FIXED_TERM_PAID'
  | 'LOW_BALANCE'
  | 'LARGE_MOVEMENT'
  | 'LOGIN'
  | 'PASSWORD_CHANGED'
  | 'TWO_FACTOR_ENABLED'
  | 'TWO_FACTOR_DISABLED';

/** Mirrors backend `NotificationDTO`: an entry of the bell. */
export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  /** Route of the app to open, e.g. `/accounts/5`. */
  link: string | null;
  createdAt: string;
  read: boolean;
}

/** Mirrors backend `AlertSettingsDTO`. Thresholds in ARS; `null` = that alert is off. */
export interface AlertSettings {
  lowBalanceThreshold: number | null;
  largeMovementThreshold: number | null;
  loginAlerts: boolean;
  emailAlerts: boolean;
}

export const NOTIFICATION_ICON: Record<NotificationType, string> = {
  WELCOME: 'waving_hand',
  TRANSFER_RECEIVED: 'call_received',
  FIXED_TERM_PAID: 'savings',
  LOW_BALANCE: 'trending_down',
  LARGE_MOVEMENT: 'payments',
  LOGIN: 'login',
  PASSWORD_CHANGED: 'password',
  TWO_FACTOR_ENABLED: 'verified_user',
  TWO_FACTOR_DISABLED: 'gpp_maybe',
};
