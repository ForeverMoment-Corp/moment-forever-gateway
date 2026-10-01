package com.forvmom.MomentForeverAPIGateway.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", String.class));
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            if (isTokenExpired(token)) {
                logger.debug("Token expired");
                return false;
            }
            // Optional: check required claims like enabled, accountNonLocked
            Boolean enabled = claims.get("enabled", Boolean.class);
            if (enabled != null && !enabled) {
                logger.debug("User disabled");
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            logger.debug("Token expired: {}", e.getMessage());
        } catch (MalformedJwtException | SignatureException e) {
            logger.debug("Invalid token: {}", e.getMessage());
        } catch (Exception e) {
            logger.debug("Token validation error: {}", e.getMessage());
        }
        return false;
    }
}