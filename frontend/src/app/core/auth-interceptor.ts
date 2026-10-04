import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { Auth } from './auth';

/**
 * Junta "Authorization: Bearer <token>" a todos os pedidos à API.
 * Se a API responder 401 (token expirado ou inválido), termina a sessão.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const token = auth.token();
  const isLogin = req.url.endsWith('/api/auth/login');

  const request =
    token && !isLogin ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isLogin) {
        auth.logout('expirou');
      }
      return throwError(() => error);
    }),
  );
};
