import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, of } from 'rxjs';

import { ClientService } from '../../core/api/client.service';
import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/auth.model';
import { Brand } from '../../shared/brand/brand';
import { initials } from '../../shared/initials';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  roles: readonly Role[];
}

const NAV: readonly NavItem[] = [
  { path: '/accounts', label: 'Mis cuentas', icon: 'account_balance_wallet', roles: ['CLIENT'] },
  { path: '/manager', label: 'Clientes', icon: 'group', roles: ['ADMIN'] },
];

@Component({
  selector: 'app-shell',
  imports: [
    Brand,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatToolbarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  private readonly auth = inject(AuthService);

  protected readonly user = toSignal(
    inject(ClientService)
      .getCurrentClient()
      .pipe(catchError(() => of(null))),
    {
      initialValue: null,
    },
  );

  protected readonly nav = computed(() => {
    const role = this.auth.role();
    return NAV.filter((item) => role !== null && item.roles.includes(role));
  });

  protected readonly initials = computed(() => {
    const user = this.user();
    return user ? initials(user.name, user.lastName) : '';
  });

  protected readonly roleLabel = computed(() =>
    this.auth.role() === 'ADMIN' ? 'Administrador' : 'Cliente',
  );

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
