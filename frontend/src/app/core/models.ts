// Tipos que espelham os DTOs devolvidos pela API.

export type Role = 'ADMIN' | 'CLIENT';
export type AccountType = 'ORDEM' | 'POUPANCA';
export type MovementType = 'SALDO_INICIAL' | 'TRANSFERENCIA_DEBITO' | 'TRANSFERENCIA_CREDITO';

export interface LoginRequest {
  utilizador: string;
  palavraPasse: string;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  expiraEmSegundos: number;
  perfil: Role;
  nome: string;
}

export interface Session {
  token: string;
  perfil: Role;
  nome: string;
  expiraEm: number; // epoch em milissegundos
}

export interface Account {
  numeroConta: string;
  nomeCliente: string;
  nuit: string;
  tipo: AccountType;
  saldo: number;
  moeda: string;
  criadaEm: string;
}

export interface CreateAccountRequest {
  nomeCliente: string;
  nuit: string;
  numeroConta: string | null;
  tipo: AccountType;
  saldoInicial: number;
  palavraPasseCliente: string | null;
}

export interface Movement {
  dataHora: string;
  tipo: MovementType;
  natureza: 'DEBITO' | 'CREDITO';
  valor: number;
  saldoResultante: number;
  descricao: string;
  contaContraparte: string | null;
  referencia: string | null;
}

export interface Page<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface Statement {
  numeroConta: string;
  nomeCliente: string;
  saldoActual: number;
  moeda: string;
  de: string | null;
  ate: string | null;
  movimentos: Page<Movement>;
}

export interface TransferRequest {
  contaOrigem: string;
  contaDestino: string;
  valor: number;
  descricao: string;
}

export interface TransferResponse {
  referencia: string;
  contaOrigem: string;
  contaDestino: string;
  valor: number;
  moeda: string;
  descricao: string;
  dataHora: string;
}

/** Erro no formato RFC 7807 devolvido pelo GlobalExceptionHandler. */
export interface ApiProblem {
  status: number;
  title: string;
  detail: string;
  codigo: string;
  erros?: Record<string, string>;
}

export interface Limits {
  numeroConta: string;
  limitePorOperacao: number;
  limiteDiario: number;
  utilizadoHoje: number;
  disponivelHoje: number;
  moeda: string;
}
