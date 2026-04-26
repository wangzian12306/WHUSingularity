package com.lubover.singularity.merchant.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    private static final String MERCHANT_ID_CLAIM = "merchantId";
    private static final String USERNAME_CLAIM = "username";
    private static final long EXPIRATION_HOURS = 24;

    private final SecretKey secretKey;
    private final JWSSigner signer;
    private final JWSVerifier verifier;

    public JwtProvider(@Value("${jwt.secret:your-super-secret-key-at-least-32-chars}") String secret) throws JOSEException {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = new SecretKeySpec(secretBytes, "HS256");
        this.signer = new MACSigner(this.secretKey);
        this.verifier = new MACVerifier(this.secretKey);
    }

    public String generateToken(Long merchantId, String username) throws JOSEException {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(EXPIRATION_HOURS * 60 * 60);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(merchantId))
                .claim(MERCHANT_ID_CLAIM, merchantId)
                .claim(USERNAME_CLAIM, username)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiration))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(this.signer);

        return signedJWT.serialize();
    }

    public JWTClaimsSet verifyToken(String token) throws TokenValidationException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(this.verifier)) {
                throw new TokenValidationException("invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            Date expirationTime = claims.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                throw new TokenValidationException("token expired");
            }

            return claims;
        } catch (ParseException e) {
            throw new TokenValidationException("invalid token format", e);
        } catch (JOSEException e) {
            throw new TokenValidationException("token verification failed", e);
        }
    }

    public Long getMerchantIdFromClaims(JWTClaimsSet claims) {
        Object merchantId = claims.getClaim(MERCHANT_ID_CLAIM);
        if (merchantId instanceof Long) {
            return (Long) merchantId;
        } else if (merchantId instanceof Integer) {
            return ((Integer) merchantId).longValue();
        } else if (merchantId instanceof String) {
            return Long.parseLong((String) merchantId);
        }
        return null;
    }

    public String getUsernameFromClaims(JWTClaimsSet claims) {
        return (String) claims.getClaim(USERNAME_CLAIM);
    }

    public long getExpireSeconds() {
        return EXPIRATION_HOURS * 60 * 60;
    }
}
