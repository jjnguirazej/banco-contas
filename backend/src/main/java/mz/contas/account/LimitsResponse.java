package mz.contas.account;

import java.math.BigDecimal;

public record LimitsResponse(String numeroConta, BigDecimal limitePorOperacao, BigDecimal limiteDiario,
                             BigDecimal utilizadoHoje, BigDecimal disponivelHoje, String moeda) {
}

