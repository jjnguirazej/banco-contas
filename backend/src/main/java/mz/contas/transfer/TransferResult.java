package mz.contas.transfer;

/** created = false quando o pedido é uma repetição idempotente de uma transferência já feita. */
public record TransferResult(TransferResponse transfer, boolean created) {
}
