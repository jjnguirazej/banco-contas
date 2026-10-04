import { Component, effect, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Api } from '../../../core/api';
import { Auth } from '../../../core/auth';
import { toUiError, toUiErrorFromBlob } from '../../../core/errors';
import { Account, Statement } from '../../../core/models';
import { LabelPipe, MoneyPipe, WhenPipe } from '../../../shared/format';

@Component({
  selector: 'app-account-detail',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe, WhenPipe, LabelPipe],
  templateUrl: './account-detail.html',
})
export class AccountDetail {
  private readonly api = inject(Api);
  readonly auth = inject(Auth);

  /** Vem da rota /contas/:numero (withComponentInputBinding). */
  readonly numero = input.required<string>();
  /** ?criada=1 depois de criar a conta. */
  readonly criada = input<string>();

  readonly account = signal<Account | null>(null);
  readonly statement = signal<Statement | null>(null);
  readonly error = signal<string | null>(null);
  readonly loadingStatement = signal(false);
  readonly downloading = signal(false);
  readonly downloadError = signal<string | null>(null);

  readonly filter = new FormGroup({
    de: new FormControl('', { nonNullable: true }),
    ate: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    // Volta a carregar sempre que o número da conta na rota muda.
    effect(() => {
      const numero = this.numero();
      this.error.set(null);
      this.account.set(null);
      this.statement.set(null);
      this.filter.reset();
      this.api.getAccount(numero).subscribe({
        next: (a) => {
          this.account.set(a);
          this.loadStatement(0);
        },
        error: (err) => this.error.set(toUiError(err).message),
      });
    });
  }

  loadStatement(pagina: number): void {
    const { de, ate } = this.filter.getRawValue();
    this.loadingStatement.set(true);
    this.api.statement(this.numero(), { de, ate, pagina }).subscribe({
      next: (s) => {
        this.statement.set(s);
        // O extracto traz o saldo actual: mantém o cabeçalho sincronizado.
        this.account.update((a) => (a ? { ...a, saldo: s.saldoActual } : a));
        this.loadingStatement.set(false);
      },
      error: (err) => {
        this.error.set(toUiError(err).message);
        this.loadingStatement.set(false);
      },
    });
  }

  /** Descarrega o PDF do período escolhido no filtro (ou de todo o histórico, sem datas). */
  downloadPdf(): void {
    const { de, ate } = this.filter.getRawValue();
    if (de && ate && de > ate) {
      this.downloadError.set('A data inicial não pode ser posterior à data final.');
      return;
    }
    this.downloading.set(true);
    this.downloadError.set(null);
    this.api.statementPdf(this.numero(), { de, ate }).subscribe({
      next: (res) => {
        const name = fileNameFrom(res.headers.get('Content-Disposition')) ?? `extracto-${this.numero()}.pdf`;
        saveBlob(res.body!, name);
        this.downloading.set(false);
      },
      error: async (err) => {
        this.downloadError.set((await toUiErrorFromBlob(err)).message);
        this.downloading.set(false);
      },
    });
  }

  clearFilter(): void {
    this.filter.reset();
    this.loadStatement(0);
  }
}

function fileNameFrom(header: string | null): string | null {
  const match = header?.match(/filename="?([^";]+)"?/);
  return match ? match[1] : null;
}

/** Cria um link temporário para o ficheiro e simula o clique, o que inicia o download. */
function saveBlob(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
