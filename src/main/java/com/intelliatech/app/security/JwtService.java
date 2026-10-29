package com.intelliatech.app.security;

import com.intelliatech.app.entity.AppUser;
import com.intelliatech.app.entity.OrganizationMembership;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    @Value("${app.security.jwt-secret}") private String secret;
    @Value("${app.security.jwt-expiration-minutes:60}") private long expirationMinutes;

    public String generate(AppUser user, OrganizationMembership membership) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(user.getId()))
                .claim("membershipId", membership.getId())
                .claim("organizationId", membership.getOrganization().getId())
                .claim("userVersion", user.getTokenVersion())
                .claim("organizationVersion", membership.getOrganization().getSecurityVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key()).compact();
    }

    public String generatePlatformAdmin(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(user.getId()))
                .claim("platformAdmin", true)
                .claim("userVersion", user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key()).compact();
    }

    public Long extractUserId(String token) { return Long.valueOf(claims(token).getSubject()); }
    public Long extractMembershipId(String token) { return ((Number) claims(token).get("membershipId")).longValue(); }
    public Long extractOrganizationId(String token) { return ((Number) claims(token).get("organizationId")).longValue(); }
    public long extractUserVersion(String token) { return ((Number) claims(token).get("userVersion")).longValue(); }
    public long extractOrganizationVersion(String token) { return ((Number) claims(token).get("organizationVersion")).longValue(); }
    public boolean isPlatformAdmin(String token) { return Boolean.TRUE.equals(claims(token).get("platformAdmin", Boolean.class)); }

    public boolean isValid(String token) { try { return claims(token).getExpiration().after(new Date()); } catch (Exception e) { return false; } }
    private Claims claims(String token) { return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload(); }
    private SecretKey key() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT secret must be at least 32 characters");
        return Keys.hmacShaKeyFor(bytes);
    }
}
