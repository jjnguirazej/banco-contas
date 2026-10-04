package mz.contas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import mz.contas.domain.AppUser;
import mz.contas.domain.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Emite e valida tokens JWT assinados com HMAC-SHA512. */
@Service
public class JwtService {

    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_CUSTOMER = "cid";

    private final SecretKey key;
    private final Duration ttl;
    private final String issuer;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl}") Duration ttl,
                      @Value("${app.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.ttl = ttl;
        this.issuer = issuer;
    }

    public String generate(AppUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getUsername())
                .issuer(issuer)
                .claim(CLAIM_UID, user.getId())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (user.getCustomer() != null) {
            builder.claim(CLAIM_CUSTOMER, user.getCustomer().getId());
        }
        return builder.signWith(key).compact();
    }

    /** @throws JwtException se o token for inválido, adulterado ou estiver expirado. */
    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number uid = claims.get(CLAIM_UID, Number.class);
        Number cid = claims.get(CLAIM_CUSTOMER, Number.class);
        return new UserPrincipal(
                uid.longValue(),
                claims.getSubject(),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                cid == null ? null : cid.longValue());
    }

    public long ttlSeconds() { return ttl.toSeconds(); }
}

