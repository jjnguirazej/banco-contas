package mz.contas.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import mz.contas.common.BusinessRuleException;
import mz.contas.common.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limites de transferência por conta de origem: um máximo por operação e um máximo diário.
 *  Valores configuráveis por variáveis de ambiente.
 */
@Component
public class TransferLimits {

    private final BigDecimal perOperation;
    private final BigDecimal daily;
    private final ZoneId zone;

    public TransferLimits(@Value("${app.limits.transfer.per-operation}") BigDecimal perOperation,
                          @Value("${app.limits.transfer.daily}") BigDecimal daily,
                          @Value("${app.timezone}") String timezone) {
        this.perOperation = perOperation;
        this.daily = daily;
        this.zone = ZoneId.of(timezone);
    }

    public void checkPerOperation(BigDecimal amount) {
        if (amount.compareTo(perOperation) > 0) {
            throw new BusinessRuleException("LIMITE_POR_OPERACAO",
                    "O valor excede o limite por operação de " + Money.format(perOperation) + " MZN.");
        }
    }

    /** usedToday: soma dos débitos de transferência da conta desde o início do dia. */
    public void checkDaily(BigDecimal usedToday, BigDecimal amount) {
        if (usedToday.add(amount).compareTo(daily) > 0) {
            throw new BusinessRuleException("LIMITE_DIARIO_EXCEDIDO",
                    "A transferência excede o limite diário de " + Money.format(daily)
                            + " MZN. Disponível hoje: " + Money.format(available(usedToday)) + " MZN.");
        }
    }

    public BigDecimal available(BigDecimal usedToday) {
        return daily.subtract(usedToday).max(BigDecimal.ZERO);
    }

    public Instant startOfToday() {
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    public BigDecimal perOperation() { return perOperation; }
    public BigDecimal daily() { return daily; }
}
