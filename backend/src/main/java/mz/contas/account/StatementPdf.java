package mz.contas.account;

/** PDF do extracto já gerado, com o nome sugerido para o ficheiro descarregado */
public record StatementPdf(byte[] content, String fileName) {
}
