export type AuditAction =
  | 'LOGIN'
  | 'LOGIN_LOCKED'
  | 'LOGOUT'
  | 'REGISTER'
  | 'PASSWORD_CHANGED'
  | 'PASSWORD_RESET_REQUESTED'
  | 'PASSWORD_RESET'
  | 'TWO_FACTOR_ENABLED'
  | 'TWO_FACTOR_DISABLED'
  | 'TWO_FACTOR_RESET'
  | 'CLIENT_CREATED'
  | 'CLIENT_BLOCKED'
  | 'CLIENT_UNBLOCKED'
  | 'ACCOUNT_OPENED'
  | 'ACCOUNT_CLOSED'
  | 'TRANSFER'
  | 'CARD_ISSUED'
  | 'CARD_DEACTIVATED'
  | 'LOAN_APPROVED'
  | 'LOAN_INSTALLMENT_PAID';

export const AUDIT_ACTION_LABEL: Record<AuditAction, string> = {
  LOGIN: 'Ingreso',
  LOGIN_LOCKED: 'Ingreso bloqueado',
  LOGOUT: 'Salida',
  REGISTER: 'Registro',
  PASSWORD_CHANGED: 'Cambio de contraseña',
  PASSWORD_RESET_REQUESTED: 'Pedido de recuperación',
  PASSWORD_RESET: 'Contraseña restablecida',
  TWO_FACTOR_ENABLED: '2FA activada',
  TWO_FACTOR_DISABLED: '2FA desactivada',
  TWO_FACTOR_RESET: '2FA quitada por admin',
  CLIENT_CREATED: 'Alta de cliente',
  CLIENT_BLOCKED: 'Cliente bloqueado',
  CLIENT_UNBLOCKED: 'Cliente desbloqueado',
  ACCOUNT_OPENED: 'Apertura de cuenta',
  ACCOUNT_CLOSED: 'Cierre de cuenta',
  TRANSFER: 'Transferencia',
  CARD_ISSUED: 'Emisión de tarjeta',
  CARD_DEACTIVATED: 'Baja de tarjeta',
  LOAN_APPROVED: 'Préstamo aprobado',
  LOAN_INSTALLMENT_PAID: 'Pago de cuota',
};

/** Mirrors backend `AuditEventDTO`. */
export interface AuditEvent {
  id: number;
  occurredAt: string;
  actor: string | null;
  actorRole: string | null;
  action: AuditAction;
  target: string | null;
  outcome: 'SUCCESS' | 'FAILURE';
  ip: string | null;
  details: string | null;
}

export type { Page } from './page.model';

export interface AuditQuery {
  actor?: string;
  action?: AuditAction;
  /** ISO instants. */
  from?: string;
  to?: string;
  page: number;
  size: number;
}
