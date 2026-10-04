import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Auth } from './auth';
import { Role } from './models';

/** Só deixa entrar utilizadores com sessão válida. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);
};

/**
 * Só deixa entrar um perfil específico. É uma ajuda de navegação:
 * a protecção real está na API, que valida o perfil em cada pedido.
 */
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    const auth = inject(Auth);
    return auth.hasRole(role) ? true : inject(Router).createUrlTree([auth.homePath()]);
  };
}
