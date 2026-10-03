package mz.contas.common;

import org.springframework.http.HttpStatus;

/** Violação de uma regra de negócio (ex.: saldo insuficiente). Devolve 422. */
public class BusinessRuleException extends ApiException {
    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
