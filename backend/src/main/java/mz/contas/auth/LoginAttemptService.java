package mz.contas.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import mz.contas.common.TooManyAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Conta as tentativas de login falhadas por utilizador no Redis.
 * Ao atingir o máximo, o utilizador fica bloqueado durante a janela configurada
 * (a chave expira sozinha graças ao TTL do Redis). Um login com sucesso limpa o contador.
 *
 * Porquê Redis e não a base de dados: é um contador temporário, e que deve
 * expirar sozinho; com várias instâncias da API, todas partilham o mesmo contador.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final String PREFIX = "login:falhas:";

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration blockDuration;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${app.security.login.max-attempts}") int maxAttempts,
                               @Value("${app.security.login.block-duration}") Duration blockDuration) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.blockDuration = blockDuration;
    }

    /** Lança 429 se o utilizador estiver bloqueado. Verifica ANTES de validar a palavra-passe. */
    public void assertNotBlocked(String username) {
        try {
            String value = redis.opsForValue().get(key(username));
            if (value != null && Integer.parseInt(value) >= maxAttempts) {
                Long ttl = redis.getExpire(key(username), TimeUnit.SECONDS);
                throw new TooManyAttemptsException(ttl == null || ttl < 0 ? blockDuration.toSeconds() : ttl);
            }
        } catch (DataAccessException ex) {
            // Se o Redis estiver indisponível, o login continua a funcionar (fail-open) e fica registado.
            log.warn("Redis indisponível ao verificar tentativas de login: {}", ex.getMessage());
        }
    }

    public void recordFailure(String username) {
        try {
            Long attempts = redis.opsForValue().increment(key(username));
            if (attempts != null && attempts == 1) {
                // A janela começa na primeira falha; a chave apaga-se sozinha no fim.
                redis.expire(key(username), blockDuration);
            }
            if (attempts != null && attempts >= maxAttempts) {
                log.warn("Utilizador {} bloqueado após {} tentativas falhadas", username, attempts);
            }
        } catch (DataAccessException ex) {
            log.warn("Redis indisponível ao registar tentativa falhada: {}", ex.getMessage());
        }
    }

    public void reset(String username) {
        try {
            redis.delete(key(username));
        } catch (DataAccessException ex) {
            log.warn("Redis indisponível ao limpar tentativas: {}", ex.getMessage());
        }
    }

    private static String key(String username) {
        return PREFIX + username.trim().toLowerCase(Locale.ROOT);
    }
}
