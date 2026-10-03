package mz.contas.common;

import org.springframework.http.HttpStatus;

/** Utilizador temporariamente bloqueado por excesso de tentativas de login falhadas. Devolve 429. */
public class TooManyAttemptsException extends ApiException {
    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADAS_TENTATIVAS",
                "Demasiadas tentativas falhadas. Tente novamente dentro de "
                        + Math.max(1, (retryAfterSeconds + 59) / 60) + " minuto(s).");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}

