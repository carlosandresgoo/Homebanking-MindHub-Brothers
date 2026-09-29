import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // errorInterceptor is outermost so it only logs failures that authInterceptor could not recover.
    provideHttpClient(withFetch(), withInterceptors([errorInterceptor, authInterceptor])),
    // Resume the session from the refresh cookie before the first navigation (guards need it).
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
