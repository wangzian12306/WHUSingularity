package com.lubover.singularity.user.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String HMAC_SHA_256 = "HmacSHA256";

    @Test
    void generatedTokenShouldContainRequiredClaims() {
        JwtProvider provider = new JwtProvider(secretKey("dev-secret-key"), 7200L);

        String token = provider.generateToken(1001L, "normal");
        JwtProvider.JwtClaims claims = provider.parseAndValidate(token);

        assertThat(token).isNotBlank();
        assertThat(claims.getSub()).isEqualTo("1001");
        assertThat(claims.getRole()).isEqualTo("normal");
        assertThat(claims.getJti()).isNotBlank();
        assertThat(claims.getExp()).isGreaterThan(claims.getIat());
    }

    @Test
    void tamperedTokenShouldFailAsInvalid() {
        JwtProvider provider = new JwtProvider(secretKey("dev-secret-key"), 7200L);
        String token = provider.generateToken(1001L, "normal");
        String[] segments = token.split("\\.");
        assertThat(segments).hasSize(3);
        String payload = segments[1];
        char replacement = payload.charAt(0) == 'a' ? 'b' : 'a';
        String tamperedPayload = replacement + payload.substring(1);
        String tampered = segments[0] + "." + tamperedPayload + "." + segments[2];

        assertThatThrownBy(() -> provider.parseAndValidate(tampered))
                .isInstanceOf(TokenValidationException.class)
                .extracting(ex -> ((TokenValidationException) ex).getReason())
                .isEqualTo(TokenValidationException.Reason.INVALID);
    }

    @Test
    void expiredTokenShouldFailAsExpired() {
        SecretKeySpec key = secretKey("dev-secret-key");
        JwtProvider provider = new JwtProvider(key, 7200L);

        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        Instant now = Instant.now();
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
            .subject("1001")
            .claim("role", "normal")
            .id(UUID.randomUUID().toString())
            .issuedAt(now.minusSeconds(7200))
            .expiresAt(now.minusSeconds(3600))
            .build();
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(headers, claimsSet)).getTokenValue();

        assertThatThrownBy(() -> provider.parseAndValidate(token))
                .isInstanceOf(TokenValidationException.class)
                .extracting(ex -> ((TokenValidationException) ex).getReason())
                .isEqualTo(TokenValidationException.Reason.EXPIRED);
    }

    private SecretKeySpec secretKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, HMAC_SHA_256);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to init test key", exception);
        }
    }
}
