package mz.contas.common;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduz todas as excepções para respostas no formato RFC 7807 (application/problem+json).
 * O cliente nunca recebe stack traces nem mensagens internas da base de dados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ResponseEntity<ProblemDetail> response = build(ex.getStatus(), ex.getCode(), ex.getMessage());
        if (ex instanceof TooManyAttemptsException tooMany) {
            // Retry-After diz ao cliente quantos segundos deve esperar.
            return ResponseEntity.status(response.getStatusCode())
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(tooMany.getRetryAfterSeconds()))
                    .body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ResponseEntity<ProblemDetail> response =
                build(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS", "Um ou mais campos são inválidos.");
        response.getBody().setProperty("erros", errors);
        return response;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ProblemDetail> handleBadInput(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "PEDIDO_INVALIDO",
                "O pedido está mal formado ou contém valores com formato inválido.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS", "Utilizador ou palavra-passe inválidos.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Não tem permissão para realizar esta operação.");
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ProblemDetail> handleLock(CannotAcquireLockException ex) {
        log.warn("Conflito de concorrência: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "OPERACAO_CONCORRENTE",
                "A conta está a ser usada noutra operação. Tente novamente.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "CONFLITO_DE_DADOS",
                "A operação viola uma regra de integridade dos dados.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Erro inesperado", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                "Ocorreu um erro inesperado. A operação não foi concluída.");
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("codigo", code);
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).body(pd);
    }
}
