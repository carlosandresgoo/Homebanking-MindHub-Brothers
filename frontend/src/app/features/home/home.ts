import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Brand } from '../../shared/brand/brand';

interface Feature {
  icon: string;
  title: string;
  text: string;
}

@Component({
  selector: 'app-home',
  imports: [Brand, MatButtonModule, MatIconModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Home {
  private readonly auth = inject(AuthService);

  /** Logged-in users go straight to their area instead of the login page. */
  protected readonly bankingLink = computed(() => {
    if (!this.auth.isAuthenticated()) return '/login';
    return this.auth.role() === 'ADMIN' ? '/manager' : '/accounts';
  });
  protected readonly isAuthenticated = this.auth.isAuthenticated;

  protected readonly year = new Date().getFullYear();

  protected readonly features: readonly Feature[] = [
    {
      icon: 'verified_user',
      title: 'Seguridad de primer nivel',
      text: 'Sesiones cifradas, cierre automático y protección frente a accesos indebidos en cada operación.',
    },
    {
      icon: 'account_balance_wallet',
      title: 'Tu dinero, siempre a mano',
      text: 'Consultá el saldo y el detalle de todas tus cuentas desde cualquier dispositivo, cuando quieras.',
    },
    {
      icon: 'support_agent',
      title: 'Atención personalizada',
      text: 'Un equipo de profesionales que conoce tus objetivos y te acompaña en cada decisión financiera.',
    },
  ];
}
