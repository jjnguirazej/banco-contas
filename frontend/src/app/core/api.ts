import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Account,
  CreateAccountRequest,
  Limits,
  Page,
  Statement,
  TransferRequest,
  TransferResponse,
} from './models';

/** Acesso aos endpoints de contas e transferências. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  listAccounts(pagina = 0, tamanho = 20): Observable<Page<Account>> {
    const params = new HttpParams().set('pagina', pagina).set('tamanho', tamanho);
    return this.http.get<Page<Account>>('/api/contas', { params });
  }

  myAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/contas/minhas');
  }

  getAccount(numero: string): Observable<Account> {
    return this.http.get<Account>(`/api/contas/${encodeURIComponent(numero)}`);
  }

  createAccount(body: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>('/api/contas', body);
  }

  statement(numero: string, filtro: { de?: string; ate?: string; pagina?: number; tamanho?: number }): Observable<Statement> {
    let params = new HttpParams()
      .set('pagina', filtro.pagina ?? 0)
      .set('tamanho', filtro.tamanho ?? 20);
    if (filtro.de) params = params.set('de', filtro.de);
    if (filtro.ate) params = params.set('ate', filtro.ate);
    return this.http.get<Statement>(`/api/contas/${encodeURIComponent(numero)}/extracto`, { params });
  }

  /** Extracto em PDF. Pedido via HttpClient (e não por link) para levar o token no cabeçalho. */
  statementPdf(numero: string, filtro: { de?: string; ate?: string }): Observable<HttpResponse<Blob>> {
    let params = new HttpParams();
    if (filtro.de) params = params.set('de', filtro.de);
    if (filtro.ate) params = params.set('ate', filtro.ate);
    return this.http.get(`/api/contas/${encodeURIComponent(numero)}/extracto/pdf`, {
      params,
      responseType: 'blob',
      observe: 'response',
    });
  }

  limits(numero: string): Observable<Limits> {
    return this.http.get<Limits>(`/api/contas/${encodeURIComponent(numero)}/limites`);
  }

  /** A chave de idempotência garante que repetir o mesmo pedido não debita duas vezes. */
  transfer(body: TransferRequest, idempotencyKey: string): Observable<TransferResponse> {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.http.post<TransferResponse>('/api/transferencias', body, { headers });
  }
}
