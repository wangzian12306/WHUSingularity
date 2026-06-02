package com.lubover.singularity.user.auth;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final long expireSeconds;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    @Autowired
    public JwtProvider(
            @Value("${auth.jwt.secret:dev-secret-key}") String secret,
            @Value("${auth.jwt.expire-seconds:7200}") long expireSeconds) {
        this(createSecretKey(secret), expireSeconds);
    }

    JwtProvider(SecretKey secretKey, long expireSeconds) {
        this.expireSeconds = expireSeconds;
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        this.jwtDecoder = decoder;
    }

    public String generateToken(Long userId, String role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(expireSeconds);

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claimsSet)).getTokenValue();
    }

    public JwtClaims parseAndValidate(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            if (issuedAt == null || expiresAt == null) {
                throw TokenValidationException.invalid("Invalid token");
            }

            JwtClaims claims = new JwtClaims();
            claims.setSub(jwt.getSubject());
            claims.setRole(jwt.getClaimAsString("role"));
            claims.setJti(jwt.getId());
            claims.setIat(issuedAt.getEpochSecond());
            claims.setExp(expiresAt.getEpochSecond());
            return claims;
        } catch (JwtValidationException exception) {
            if (isExpired(exception)) {
                throw TokenValidationException.expired("Token expired");
            }
            throw TokenValidationException.invalid("Invalid token");
        } catch (BadJwtException exception) {
            throw TokenValidationException.invalid("Invalid token");
        } catch (JwtException exception) {
            throw TokenValidationException.invalid("Invalid token");
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    private static SecretKey createSecretKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, HMAC_SHA_256);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize secret key", exception);
        }
    }

    private boolean isExpired(JwtValidationException exception) {
        return exception.getErrors().stream()
                .map(error -> error.getDescription() == null ? "" : error.getDescription().toLowerCase())
                .anyMatch(description -> description.contains("expired"));
    }

    public static class JwtClaims {
        private String sub;
        private String role;
        private String jti;
        private long iat;
        private long exp;

        public String getSub() {
            return sub;
        }

        public void setSub(String sub) {
            this.sub = sub;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getJti() {
            return jti;
        }

        public void setJti(String jti) {
            this.jti = jti;
        }

        public long getIat() {
            return iat;
        }

        public void setIat(long iat) {
            this.iat = iat;
        }

        public long getExp() {
            return exp;
        }

        public void setExp(long exp) {
            this.exp = exp;
        }
    }
}
