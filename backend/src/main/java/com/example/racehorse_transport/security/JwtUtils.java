package com.example.racehorse_transport.security;

import com.example.racehorse_transport.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class JwtUtils {

    // Claim phân loại token: access token gửi trong header, refresh token chỉ nằm trong cookie.
    // Tách loại để refresh token (hạn dài) không dùng được thay access token.
    static final String TYPE_CLAIM = "typ";
    static final String ACCESS = "access";
    static final String REFRESH = "refresh";

    private final JwtProperties jwtProperties;

    // Danh sách đen token đã thu hồi (Revoked / Blacklisted tokens) khi Đăng xuất
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    @Value("${app.jwt.access-token-expiration-ms:900000}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration-ms:1296000000}")
    private long refreshTokenExpirationMs;

    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.signingKey());
    }

    // Đóng gói Refresh Token vào HttpOnly Cookie của đúng ứng dụng
    public ResponseCookie generateRefreshCookie(String username, ClientApp app) {
        String refreshToken = generateRefreshToken(username);
        return ResponseCookie.from(app.cookieName(), refreshToken)
                .path("/")
                .maxAge(refreshTokenExpirationMs / 1000)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .build();
    }

    // Cookie xóa refresh token khi đăng xuất
    public ResponseCookie getCleanRefreshCookie(ClientApp app) {
        return ResponseCookie.from(app.cookieName(), "")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .build();
    }

    // Lấy refresh token của ứng dụng từ request cookie
    public String getRefreshTokenFromCookies(HttpServletRequest request, ClientApp app) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (app.cookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    // 1. Sinh Access Token (thời hạn 15 phút). Mỗi tài khoản một vai trò (app_user.role_code);
    //    claim "role" giữ vai trò để tương thích giao diện cũ, claim "roles" là danh sách một phần tử.
    public String generateToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .setSubject(username)
                .claim(TYPE_CLAIM, ACCESS)
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
                .claim(TYPE_CLAIM, REFRESH)
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

    // Access token hợp lệ: đúng chữ ký, chưa hết hạn, chưa thu hồi, đúng loại access
    public boolean validateAccessToken(String token) {
        return hasType(token, ACCESS);
    }

    // Refresh token hợp lệ: đúng chữ ký, chưa hết hạn, chưa thu hồi, đúng loại refresh
    public boolean validateRefreshToken(String token) {
        return hasType(token, REFRESH);
    }

    private boolean hasType(String token, String type) {
        if (token == null || token.isBlank() || isBlacklisted(token)) {
            return false;
        }
        try {
            return type.equals(extractClaims(token).get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            // Token không hợp lệ hoặc đã hết hạn
            return false;
        }
    }
}
