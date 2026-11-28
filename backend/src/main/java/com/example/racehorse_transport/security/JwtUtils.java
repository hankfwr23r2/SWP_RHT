package com.example.racehorse_transport.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtUtils {

    @Value("${app.jwt.secret:${APP_JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}}")
    private String jwtSecret;

    // Danh sách đen token đã thu hồi (Revoked / Blacklisted tokens) khi Đăng xuất
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    @Value("${app.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms:1296000000}")
    private long refreshTokenExpirationMs;

    @Value("${app.jwt.refresh-cookie-name:refreshToken}")
    private String refreshCookieName;

    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // Đóng gói Refresh Token vào HttpOnly Cookie
    public ResponseCookie generateRefreshCookie(String username) {
        String refreshToken = generateRefreshToken(username);
        return ResponseCookie.from(refreshCookieName, refreshToken)
                .path("/")
                .maxAge(refreshTokenExpirationMs / 1000)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .build();
    }

    // Cookie xóa refresh token khi đăng xuất
    public ResponseCookie getCleanRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .build();
    }

    // Lấy refresh token từ request cookie
    public String getRefreshTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (refreshCookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    // 1. Sinh Access Token (thời hạn 15 phút). roles lấy từ bảng user_role, phần tử đầu là vai trò chính;
    //    claim "role" giữ vai trò chính để tương thích giao diện cũ, claim "roles" chứa đủ danh sách.
    public String generateToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", roles.isEmpty() ? null : roles.get(0))
                .claim("roles", roles)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. Sinh Refresh Token từ username (thời hạn 15 ngày)
    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpirationMs);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // 3. Kiểm tra token đã hết hạn hay chưa
    public boolean isTokenExpired(String token) {
        try {
            return extractClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    // Lấy username từ JWT token
    public String getUsernameFromToken(String token) {
        return extractClaims(token).getSubject();
    }

    // Lấy danh sách vai trò từ JWT token
    public List<String> getRolesFromToken(String token) {
        Object roles = extractClaims(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    // Trích xuất toàn bộ Claims từ token
    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Thu hồi token (đưa vào blacklist khi đăng xuất)
    public void blacklistToken(String token) {
        if (token != null && !token.isBlank()) {
            blacklistedTokens.add(token.trim());
        }
    }

    public boolean isBlacklisted(String token) {
        return token != null && blacklistedTokens.contains(token.trim());
    }

    // Kiểm tra tính hợp lệ của token (bao gồm cả kiểm tra blacklist)
    public boolean validateToken(String token) {
        if (token == null || token.isBlank() || isBlacklisted(token)) {
            return false;
        }
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token không hợp lệ hoặc đã hết hạn
            return false;
        }
    }
}
