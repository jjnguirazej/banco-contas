package mz.contas.account;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceResponse(String numeroConta, BigDecimal saldo, String moeda, Instant consultadoEm) {
}

