package mz.contas.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import mz.contas.domain.Movement;
import mz.contas.domain.MovementType;
import mz.contas.domain.Transfer;

public record MovementResponse(Instant dataHora, MovementType tipo, MovementType.Nature natureza,
                               BigDecimal valor, BigDecimal saldoResultante, String descricao,
                               String contaContraparte, UUID referencia) {

    public static MovementResponse from(Movement m) {
        Transfer t = m.getTransfer();
        String counterpart = null;
        UUID reference = null;
        if (t != null) {
            reference = t.getReference();
            counterpart = m.getType() == MovementType.TRANSFERENCIA_DEBITO
                    ? t.getTargetAccount().getAccountNumber()
                    : t.getSourceAccount().getAccountNumber();
        }
        return new MovementResponse(m.getCreatedAt(), m.getType(), m.getType().getNature(), m.getAmount(),
                m.getBalanceAfter(), m.getDescription(), counterpart, reference);
    }
}
