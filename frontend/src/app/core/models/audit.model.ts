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
  | 'ACCOUNT_ALIAS_CHANGED'
  | 'TRANSFER'
  | 'SCHEDULED_TRANSFER_CREATED'
  | 'SCHEDULED_TRANSFER_CANCELLED'
  | 'SCHEDULED_TRANSFER_FAILED'
  | 'CONTACT_ADDED'
  | 'CONTACT_REMOVED'
  | 'CONTACT_TRUSTED'
  | 'CONTACT_UNTRUSTED'
  | 'CARD_ISSUED'
  | 'CARD_DEACTIVATED'
  | 'LOAN_APPROVED'
  | 'LOAN_INSTALLMENT_PAID'
  | 'FIXED_TERM_CREATED'
  | 'FIXED_TERM_PAID'
  | 'FIXED_TERM_RENEWED';

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
  ACCOUNT_ALIAS_CHANGED: 'Cambio de alias',
  TRANSFER: 'Transferencia',
  SCHEDULED_TRANSFER_CREATED: 'Transferencia programada',
  SCHEDULED_TRANSFER_CANCELLED: 'Programación cancelada',
  SCHEDULED_TRANSFER_FAILED: 'Programada fallida',
  CONTACT_ADDED: 'Destinatario agregado',
  CONTACT_REMOVED: 'Destinatario borrado',
  CONTACT_TRUSTED: 'Destinatario de confianza',
  CONTACT_UNTRUSTED: 'Confianza quitada',
  CARD_ISSUED: 'Emisión de tarjeta',
  CARD_DEACTIVATED: 'Baja de tarjeta',
  LOAN_APPROVED: 'Préstamo aprobado',
  LOAN_INSTALLMENT_PAID: 'Pago de cuota',
  FIXED_TERM_CREATED: 'Plazo fijo constituido',
  FIXED_TERM_PAID: 'Plazo fijo acreditado',
  FIXED_TERM_RENEWED: 'Plazo fijo renovado',
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
