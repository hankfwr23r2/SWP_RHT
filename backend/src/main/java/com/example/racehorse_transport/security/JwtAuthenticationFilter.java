package com.example.racehorse_transport.security;

import com.example.racehorse_transport.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateToken(jwt)) {
                String username = jwtUtils.getUsernameFromToken(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Tài khoản bị khóa, vô hiệu hóa hoặc nhân viên đã nghỉ việc: token cũ không còn dùng được
                    if (!authService.isActive(username)) {
                        log.warn("Từ chối token của tài khoản không còn hoạt động: {}", username);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities(jwtUtils.getRolesFromToken(jwt)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Không thể xác thực người dùng từ JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> authorities(List<String> roles) {
        Set<String> names = new LinkedHashSet<>();
        for (String role : roles) {
            if (StringUtils.hasText(role)) {
                names.add("ROLE_" + role.toUpperCase(Locale.ROOT));
            }
        }
        // ADMIN được thao tác thay các khối quản lý, điều phối, chuyên viên để hỗ trợ kiểm tra
        if (names.contains("ROLE_ADMIN")) {
            names.add("ROLE_MANAGER");
            names.add("ROLE_COORDINATOR");
            names.add("ROLE_SPECIALIST");
        }
        return names.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
