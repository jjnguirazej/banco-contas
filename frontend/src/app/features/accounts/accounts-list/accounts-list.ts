import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Api } from '../../../core/api';
import { toUiError } from '../../../core/errors';
import { Account, Page } from '../../../core/models';
import { LabelPipe, MoneyPipe, WhenPipe } from '../../../shared/format';

@Component({
  selector: 'app-accounts-list',
  imports: [RouterLink, MoneyPipe, WhenPipe, LabelPipe],
  templateUrl: './accounts-list.html',
})
export class AccountsList {
  private readonly api = inject(Api);
  readonly router = inject(Router);

  readonly page = signal<Page<Account> | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.load(0);
  }

  load(pagina: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listAccounts(pagina).subscribe({
      next: (p) => {
        this.page.set(p);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toUiError(err).message);
        this.loading.set(false);
      },
    });
  }

  open(account: Account): void {
    this.router.navigate(['/contas', account.numeroConta]);
  }
}
