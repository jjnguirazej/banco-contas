import { inject } from '@angular/core';
import { Routes } from '@angular/router';

import { Auth } from './core/auth';
import { authGuard, roleGuard } from './core/auth-guard';
import { Login } from './features/auth/login/login';
import { Shell } from './features/shell/shell';
import { AccountsList } from './features/accounts/accounts-list/accounts-list';
import { AccountCreate } from './features/accounts/account-create/account-create';
import { MyAccounts } from './features/accounts/my-accounts/my-accounts';
import { AccountDetail } from './features/accounts/account-detail/account-detail';
import { Transfer } from './features/transfer/transfer';

export const routes: Routes = [
  { path: 'login', component: Login, title: 'Entrar · Contas' },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      // Página inicial depende do perfil: administrador vê todas as contas, cliente vê as suas.
      { path: '', pathMatch: 'full', redirectTo: () => inject(Auth).homePath() },
      { path: 'contas', component: AccountsList, canActivate: [roleGuard('ADMIN')], title: 'Contas' },
      { path: 'contas/nova', component: AccountCreate, canActivate: [roleGuard('ADMIN')], title: 'Nova conta' },
      { path: 'contas/:numero', component: AccountDetail, title: 'Conta' },
      { path: 'minhas-contas', component: MyAccounts, canActivate: [roleGuard('CLIENT')], title: 'As minhas contas' },
      { path: 'transferir', component: Transfer, title: 'Transferir' },
    ],
  },
  { path: '**', redirectTo: '' },
];
