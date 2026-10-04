package mz.contas.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import mz.contas.common.PageResponse;

public record StatementResponse(String numeroConta, String nomeCliente, BigDecimal saldoActual, String moeda,
                                LocalDate de, LocalDate ate, PageResponse<MovementResponse> movimentos) {
}
