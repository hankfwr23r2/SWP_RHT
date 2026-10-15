package com.example.racehorse_transport.security;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtUtils.validateToken(jwt)) {
                String username = jwtUtils.getUsernameFromToken(jwt);
                String role = jwtUtils.getRoleFromToken(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Kiểm tra xem tài khoản có bị khóa hoặc vô hiệu hóa trong CSDL hay không
                    Optional<User> userOpt = userRepository.findByUsername(username);
                    if (userOpt.isPresent()) {
                        Integer userId = userOpt.get().getId();
                        Optional<Staff> staffOpt = staffRepository.findById(userId);
                        if (staffOpt.isPresent() && "INACTIVE".equalsIgnoreCase(staffOpt.get().getEmploymentStatus())) {
                            log.warn("Tài khoản nhân viên {} (ID: {}) đã bị vô hiệu hóa, từ chối xác thực token", username, userId);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }

                    List<GrantedAuthority> authorities = new ArrayList<>();
                    if (StringUtils.hasText(role)) {
                        String primaryRole = role.toUpperCase();
                        if (!primaryRole.startsWith("ROLE_")) {
                            primaryRole = "ROLE_" + primaryRole;
                        }
                        authorities.add(new SimpleGrantedAuthority(primaryRole));

                        // Nếu là ADMIN, cấp thêm quyền quản trị các khối khác để hỗ trợ kiểm tra/điều phối
                        if ("ROLE_ADMIN".equals(primaryRole)) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
                            authorities.add(new SimpleGrantedAuthority("ROLE_COORDINATOR"));
                            authorities.add(new SimpleGrantedAuthority("ROLE_SPECIALIST"));
                        }
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Không thể xác thực người dùng từ JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
