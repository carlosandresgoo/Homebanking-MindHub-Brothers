import { JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClientService } from '../../core/api/client.service';
import { toLoadState } from '../../core/utils/load-state';

const LETTERS_ONLY = /^[a-zA-Z]+$/;

@Component({
  selector: 'app-manager',
  imports: [JsonPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './manager.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Manager {
  private readonly clientService = inject(ClientService);

  protected readonly state = toSignal(toLoadState(this.clientService.getClients()), {
    requireSync: true,
  });

  /** Raw API response shown in the "Client REST response" panel (`[]` until loaded, like the Vue page). */
  protected readonly clientsJson = computed(() => {
    const s = this.state();
    return s.status === 'loaded' ? s.data : [];
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.pattern(LETTERS_ONLY)]],
    lastName: ['', [Validators.required, Validators.pattern(LETTERS_ONLY)]],
    email: ['', [Validators.required, Validators.email]],
  });

  protected addClient(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    // The backend has no create-client endpoint yet (the Vue page never implemented it either).
    // It will be added in phase 3 as a validated, ADMIN-only POST /api/clients.
  }
}
