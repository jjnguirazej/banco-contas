import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Api } from '../../core/api';
import { Auth } from '../../core/auth';
import { toUiError } from '../../core/errors';
import { Account, Limits, TransferRequest, TransferResponse } from '../../core/models';
import { MoneyPipe, WhenPipe } from '../../shared/format';

type Step = 'preencher' | 'rever' | 'concluida';

@Component({
  selector: 'app-transfer',
  imports: [ReactiveFormsModule, RouterLink, MoneyPipe, WhenPipe],
  templateUrl: './transfer.html',
})
export class Transfer implements OnInit {
  private readonly api = inject(Api);
  readonly auth = inject(Auth);

  readonly origem = input<string>();

  readonly step = signal<Step>('preencher');
  readonly myAccounts = signal<Account[]>([]);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly result = signal<TransferResponse | null>(null);
  /** Limites da conta de origem: mostrados junto ao valor para o utilizador saber quanto pode transferir. */
  readonly limits = signal<Limits | null>(null);
  private readonly destroyRef = inject(DestroyRef);

  /** Uma chave por operação: se o "Confirmar" for repetido após uma falha de rede, não há débito duplo. */
  private idempotencyKey = '';

  readonly form = inject(NonNullableFormBuilder).group({
    contaOrigem: ['', Validators.required],
    contaDestino: ['', Validators.required],
    valor: [null as number | null, [Validators.required, Validators.min(0.01)]],
    descricao: ['', [Validators.required, Validators.maxLength(200)]],
  });

  ngOnInit(): void {
    // Sempre que a conta de origem muda, vai buscar os limites dessa conta.
    this.form.controls.contaOrigem.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((numero) =>
          numero && numero.trim().length >= 8
            ? this.api.limits(numero.trim()).pipe(catchError(() => of(null)))
            : of(null),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((l) => this.limits.set(l));

    // Os inputs da rota (ex.: ?origem=) só estão disponíveis a partir do ngOnInit.
    if (this.auth.isAdmin()) {
      this.prefillOrigin();
    } else {
      // O cliente só pode transferir a partir das suas contas: mostramos apenas essas.
      this.api.myAccounts().subscribe({
        next: (list) => {
          this.myAccounts.set(list);
          this.prefillOrigin(list[0]?.numeroConta);
        },
        error: (err) => this.error.set(toUiError(err).message),
      });
    }
  }

  review(): void {
    const { contaOrigem, contaDestino } = this.form.getRawValue();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (contaOrigem.trim() === contaDestino.trim()) {
      this.error.set('A conta de destino tem de ser diferente da conta de origem.');
      return;
    }
    this.error.set(null);
    this.idempotencyKey = newKey();
    this.step.set('rever');
  }

  back(): void {
    this.error.set(null);
    this.step.set('preencher');
  }

  confirm(): void {
    const v = this.form.getRawValue();
    const body: TransferRequest = {
      contaOrigem: v.contaOrigem.trim(),
      contaDestino: v.contaDestino.trim(),
      valor: Number(v.valor),
      descricao: v.descricao.trim(),
    };
    this.sending.set(true);
    this.error.set(null);
    this.api.transfer(body, this.idempotencyKey).subscribe({
      next: (res) => {
        this.result.set(res);
        this.refreshLimits();
        this.step.set('concluida');
        this.sending.set(false);
      },
      error: (err) => {
        this.error.set(toUiError(err).message);
        this.sending.set(false);
      },
    });
  }

  newTransfer(): void {
    const origem = this.form.controls.contaOrigem.value;
    this.form.reset({ contaOrigem: origem, contaDestino: '', valor: null, descricao: '' });
    this.result.set(null);
    this.error.set(null);
    this.step.set('preencher');
  }

  private refreshLimits(): void {
    const numero = this.form.controls.contaOrigem.value.trim();
    if (numero) {
      this.api.limits(numero).pipe(catchError(() => of(null))).subscribe((l) => this.limits.set(l));
    }
  }

  invalid(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.touched && c.invalid;
  }

  private prefillOrigin(fallback?: string): void {
    const value = this.origem() ?? fallback;
    if (value) {
      this.form.controls.contaOrigem.setValue(value);
    }
  }
}

function newKey(): string {
  // randomUUID só existe em contextos seguros (https ou localhost).
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}
