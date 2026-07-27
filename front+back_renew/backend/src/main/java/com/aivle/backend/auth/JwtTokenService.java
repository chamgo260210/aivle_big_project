package com.aivle.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {
    static final String ACCESS_TYPE = "ACCESS";
    static final String REFRESH_TYPE = "REFRESH";

    private final JwtEncoder jwtEncoder;
    @Qualifier("refreshTokenDecoder")
    private final JwtDecoder refreshTokenDecoder;
    private final JwtProperties properties;
    private final Clock jobClock;

    public IssuedTokenPair issue(Long userId) {
        Instant now = jobClock.instant();
        IssuedToken access = encode(
            userId,
            ACCESS_TYPE,
            now,
            now.plus(properties.accessTokenTtl())
        );
        IssuedToken refresh = encode(
            userId,
            REFRESH_TYPE,
            now,
            now.plus(properties.refreshTokenTtl())
        );
        return new IssuedTokenPair(access, refresh);
    }

    public Jwt decodeRefresh(String rawToken) {
        return refreshTokenDecoder.decode(rawToken);
    }

    private IssuedToken encode(
        Long userId,
        String tokenType,
        Instant issuedAt,
        Instant expiresAt
    ) {
        String jti = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .id(jti)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("tokenType", tokenType)
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(
            JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
        return new IssuedToken(value, jti, expiresAt);
    }

    public record IssuedToken(String value, String jti, Instant expiresAt) {
    }

    public record IssuedTokenPair(IssuedToken access, IssuedToken refresh) {
    }
}
