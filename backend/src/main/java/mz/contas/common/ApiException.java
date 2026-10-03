package mz.contas.common;

import org.springframework.http.HttpStatus;

/** Excepção base da aplicação: transporta o estado HTTP e um código de negócio estável. */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
