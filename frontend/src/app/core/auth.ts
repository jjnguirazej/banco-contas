import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { LoginRequest, LoginResponse, Role, Session } from './models';

const STORAGE_KEY = 'contas.sessao';

/** Guarda a sessão (token JWT, perfil, nome) e expõe-na como signals. */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session());
  readonly isAdmin = computed(() => this.session()?.perfil === 'ADMIN');

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', request).pipe(
      tap((res) => {
        const session: Session = {
          token: res.token,
          perfil: res.perfil,
          nome: res.nome,
          expiraEm: Date.now() + res.expiraEmSegundos * 1000,
        };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.session.set(session);
      }),
    );
  }

  logout(reason?: 'expirou'): void {
    localStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
    this.router.navigate(['/login'], { queryParams: reason ? { motivo: reason } : {} });
  }

  token(): string | null {
    const s = this.session();
    if (!s || s.expiraEm <= Date.now()) {
      return null;
    }
    return s.token;
  }

  isLoggedIn(): boolean {
    return this.token() !== null;
  }

  hasRole(role: Role): boolean {
    return this.session()?.perfil === role;
  }

  homePath(): string {
    return this.isAdmin() ? '/contas' : '/minhas-contas';
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const s = raw ? (JSON.parse(raw) as Session) : null;
      return s && s.expiraEm > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
