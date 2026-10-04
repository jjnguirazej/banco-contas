package mz.contas.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import mz.contas.domain.Transfer;

public record TransferResponse(UUID referencia, String contaOrigem, String contaDestino, BigDecimal valor,
                               String moeda, String descricao, Instant dataHora) {

    public static TransferResponse from(Transfer t) {
        return new TransferResponse(t.getReference(), t.getSourceAccount().getAccountNumber(),
                t.getTargetAccount().getAccountNumber(), t.getAmount(), "MZN", t.getDescription(), t.getCreatedAt());
    }
}

