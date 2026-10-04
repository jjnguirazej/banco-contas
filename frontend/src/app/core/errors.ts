import { HttpErrorResponse } from '@angular/common/http';

import { ApiProblem } from './models';

export interface UiError {
  message: string;
  fields: Record<string, string>;
}

/** Converte um erro HTTP numa mensagem para o utilizador e nos erros por campo. */
export function toUiError(error: unknown): UiError {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { message: 'Não foi possível contactar o servidor. Confirme que a API está a correr.', fields: {} };
    }
    const problem = error.error as Partial<ApiProblem> | null;
    if (problem && typeof problem === 'object' && problem.detail) {
      return { message: problem.detail, fields: problem.erros ?? {} };
    }
  }
  return { message: 'Ocorreu um erro inesperado. Tente novamente.', fields: {} };
}

/**
 * Igual a toUiError, mas para pedidos com responseType 'blob' (ex.: o PDF):
 * aí o corpo do erro chega como Blob e tem de ser lido como texto antes de interpretar.
 */
export async function toUiErrorFromBlob(error: unknown): Promise<UiError> {
  if (error instanceof HttpErrorResponse && error.error instanceof Blob) {
    try {
      const problem = JSON.parse(await error.error.text()) as Partial<ApiProblem>;
      if (problem.detail) {
        return { message: problem.detail, fields: problem.erros ?? {} };
      }
    } catch {
      // corpo não é JSON: fica como mensagem genérica
    }
  }
  return toUiError(error);
}
