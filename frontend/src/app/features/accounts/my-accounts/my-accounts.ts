import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Api } from '../../../core/api';
import { Auth } from '../../../core/auth';
import { toUiError } from '../../../core/errors';
import { Account } from '../../../core/models';
import { LabelPipe, MoneyPipe } from '../../../shared/format';

@Component({
  selector: 'app-my-accounts',
  imports: [RouterLink, MoneyPipe, LabelPipe],
  templateUrl: './my-accounts.html',
})
export class MyAccounts {
  private readonly api = inject(Api);
  readonly auth = inject(Auth);

  readonly accounts = signal<Account[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly total = computed(() => (this.accounts() ?? []).reduce((sum, a) => sum + a.saldo, 0));

  constructor() {
    this.api.myAccounts().subscribe({
      next: (list) => this.accounts.set(list),
      error: (err) => this.error.set(toUiError(err).message),
    });
  }
}
