import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Api } from '../../../core/api';
import { toUiError } from '../../../core/errors';
import { AccountType } from '../../../core/models';

@Component({
  selector: 'app-account-create',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './account-create.html',
})
export class AccountCreate {
  private readonly api = inject(Api);
  private readonly router = inject(Router);

  // As mesmas regras da API, para dar feedback imediato. A API valida sempre de novo.
  readonly form = inject(NonNullableFormBuilder).group({
    nomeCliente: ['', [Validators.required, Validators.maxLength(150)]],
    nuit: ['', [Validators.required, Validators.pattern(/^\d{9}$/)]],
    tipo: ['ORDEM' as AccountType, Validators.required],
    saldoInicial: [0, [Validators.required, Validators.min(0)]],
    numeroConta: ['', Validators.pattern(/^\d{8,20}$/)],
    palavraPasseCliente: ['', [Validators.minLength(8), Validators.maxLength(72)]],
  });

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly serverErrors = signal<Record<string, string>>({});

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.serverErrors.set({});

    this.api
      .createAccount({
        nomeCliente: v.nomeCliente.trim(),
        nuit: v.nuit,
        tipo: v.tipo,
        saldoInicial: Number(v.saldoInicial),
        numeroConta: v.numeroConta || null,
        palavraPasseCliente: v.palavraPasseCliente || null,
      })
      .subscribe({
        next: (account) =>
          this.router.navigate(['/contas', account.numeroConta], { queryParams: { criada: 1 } }),
        error: (err) => {
          const ui = toUiError(err);
          this.error.set(ui.message);
          this.serverErrors.set(ui.fields);
          this.saving.set(false);
        },
      });
  }

  invalid(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.touched && c.invalid;
  }
}
