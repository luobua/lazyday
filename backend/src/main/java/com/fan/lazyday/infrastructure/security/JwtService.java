package com.fan.lazyday.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtService {

    private static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofHours(2);
    private static final Duration REFRESH_TOKEN_EXPIRY = Duration.ofDays(7);
    private static final Duration REFRESH_TOKEN_REMEMBER_EXPIRY = Duration.ofDays(30);

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private JWSSigner signer;
    private JWSVerifier verifier;

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.signer = new RSASSASigner(privateKey);
        this.verifier = new RSASSAVerifier(publicKey);
        log.info("JWT RS256 key pair initialized");
    }

    public String generateAccessToken(Long userId, Long tenantId, String role) {
        return generateToken(userId, tenantId, role, ACCESS_TOKEN_EXPIRY);
    }

    public String generateRefreshToken(Long userId, Long tenantId, String role, boolean remember) {
        Duration expiry = remember ? REFRESH_TOKEN_REMEMBER_EXPIRY : REFRESH_TOKEN_EXPIRY;
        return generateToken(userId, tenantId, role, expiry);
    }

    public Duration getAccessTokenExpiry() {
        return ACCESS_TOKEN_EXPIRY;
    }

    public Duration getRefreshTokenExpiry(boolean remember) {
        return remember ? REFRESH_TOKEN_REMEMBER_EXPIRY : REFRESH_TOKEN_EXPIRY;
    }

    private String generateToken(Long userId, Long tenantId, String role, Duration expiry) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))
                    .claim("tenantId", tenantId)
                    .claim("role", role)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(expiry)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.RS256),
                    claims
            );
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    public JwtClaims validateToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
                return null;
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                return JwtClaims.expired();
            }
            return new JwtClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.getLongClaim("tenantId"),
                    claims.getStringClaim("role"),
                    false
            );
        } catch (ParseException | JOSEException e) {
            return null;
        }
    }

    @Getter
    public static class JwtClaims {
        private final Long userId;
        private final Long tenantId;
        private final String role;
        private final boolean expired;

        public JwtClaims(Long userId, Long tenantId, String role, boolean expired) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.role = role;
            this.expired = expired;
        }

        public static JwtClaims expired() {
            return new JwtClaims(null, null, null, true);
        }
    }
}
