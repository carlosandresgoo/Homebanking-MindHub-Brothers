import { JsonPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, switchMap } from 'rxjs';

import { ClientService } from '../../core/api/client.service';
import { AuthService } from '../../core/auth/auth.service';
import { toLoadState } from '../../core/utils/load-state';

const LETTERS_ONLY = /^[a-zA-Z]+$/;
/** Same limits as the backend's CreateClientRequest (BCrypt ignores bytes past 72). */
const PASSWORD_MIN = 12;
const PASSWORD_MAX = 72;

@Component({
  selector: 'app-manager',
  imports: [JsonPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './manager.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Manager {
  private readonly clientService = inject(ClientService);
  private readonly auth = inject(AuthService);
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  protected readonly state = toSignal(
    this.reload$.pipe(switchMap(() => toLoadState(this.clientService.getClients()))),
    { requireSync: true },
  );

  /** Raw API response shown in the "Client REST response" panel (`[]` until loaded, like the Vue page). */
  protected readonly clientsJson = computed(() => {
    const s = this.state();
    return s.status === 'loaded' ? s.data : [];
  });

  protected readonly submitting = signal(false);
  protected readonly feedback = signal<{ kind: 'success' | 'danger'; text: string } | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(LETTERS_ONLY)]],
    lastName: ['', [Validators.required, Validators.maxLength(50), Validators.pattern(LETTERS_ONLY)]],
    email: ['', [Validators.required, Validators.email]],
    password: [
      '',
      [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)],
    ],
  });

  protected addClient(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.feedback.set(null);
    this.clientService.createClient(this.form.getRawValue()).subscribe({
      next: (client) => {
        this.submitting.set(false);
        this.form.reset();
        this.feedback.set({ kind: 'success', text: `Client ${client.email} created.` });
        this.reload$.next();
      },
      error: (err: unknown) => {
        this.submitting.set(false);
        this.form.controls.password.reset();
        this.feedback.set({ kind: 'danger', text: messageFor(err) });
      },
    });
  }

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}

function messageFor(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 409) return 'That email is already registered.';
    if (err.status === 400) return 'Some fields are invalid. Please review the form.';
    if (err.status === 403) return 'You are not allowed to create clients.';
  }
  return 'Could not create the client. Please try again later.';
}
