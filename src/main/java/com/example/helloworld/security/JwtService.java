package com.example.helloworld.security;

import com.example.helloworld.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final String ISSUER = "springboot-helloworld";

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        this.expiration = properties.expiration();
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalStateException("app.jwt.expiration must be a positive duration");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(properties.secret());
        } catch (DecodingException ex) {
            throw new IllegalStateException("app.jwt.secret must be Base64-encoded", ex);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedToken issueToken(String username) {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(expiration);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiresAt, expiration.toSeconds());
    }

    /**
     * Returns the subject only when the token has a valid signature, issuer,
     * and expiry. Invalid tokens are treated as unauthenticated.
     */
    public Optional<String> extractUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // JJWT validates an expiry that is present; also require one to exist.
            if (claims.getExpiration() == null) {
                return Optional.empty();
            }

            return StringUtils.hasText(claims.getSubject())
                    ? Optional.of(claims.getSubject())
                    : Optional.empty();
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
