import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { Auth } from '../../../core/auth';
import { toUiError } from '../../../core/errors';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  /** ?motivo=expirou quando a sessão terminou por token expirado. */
  readonly motivo = input<string>();

  readonly form = inject(NonNullableFormBuilder).group({
    utilizador: ['', Validators.required],
    palavraPasse: ['', Validators.required],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    if (this.auth.isLoggedIn()) {
      this.router.navigateByUrl(this.auth.homePath());
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl(this.auth.homePath()),
      error: (err) => {
        this.error.set(toUiError(err).message);
        this.loading.set(false);
      },
    });
  }
}

