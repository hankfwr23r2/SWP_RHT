package com.example.racehorse_transport.config;

import com.example.racehorse_transport.security.JwtAccessDeniedHandler;
import com.example.racehorse_transport.security.JwtAuthenticationEntryPoint;
import com.example.racehorse_transport.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // Bean mã hóa mật khẩu bằng thuật toán BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Cấu hình chuỗi lọc bảo mật (Security Filter Chain)
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Kích hoạt CORS theo cấu hình CorsConfigurationSource
                .cors(Customizer.withDefaults())
                
                // Tắt CSRF vì REST API dùng JWT (stateless) không cần CSRF token
                .csrf(AbstractHttpConfigurer::disable)
                
                // Xử lý lỗi xác thực và phân quyền chuẩn JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                
                // Cấu hình Session là STATELESS (không lưu session trên server)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                
                // Cấu hình phân quyền truy cập URL
                .authorizeHttpRequests(auth -> auth
                        // Cho phép truy cập tự do vào các API Auth, Swagger UI tài liệu API, và tra cứu đơn công khai
                        .requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/bookings/public-track").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // VNPAY gọi IPN server-to-server và trang kết quả gửi lại tham số: bảo vệ bằng chữ ký HMAC, không cần token
                        .requestMatchers(HttpMethod.GET, "/payments/vnpay/ipn", "/payments/vnpay/return").permitAll()

                        // Phân quyền API Admin: Chỉ duy nhất tài khoản có ROLE_ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Phân quyền API Manager: Chỉ Quản lý và Admin
                        .requestMatchers("/manager/**", "/trip-reports/**").hasAnyRole("MANAGER", "ADMIN")

                        // Phân quyền API Điều phối viên: Coordinator, Manager, Admin
                        .requestMatchers("/coordinator/**").hasAnyRole("COORDINATOR", "MANAGER", "ADMIN")

                        // Phân quyền API Kiểm dịch viên: Specialist, Inspector, Manager, Admin
                        .requestMatchers("/specialist/**").hasAnyRole("SPECIALIST", "INSPECTOR", "MANAGER", "ADMIN")

                        // Phân quyền API Khách hàng: Customer, Admin
                        .requestMatchers("/customer/**").hasAnyRole("CUSTOMER", "ADMIN")

                        // Thông báo nội bộ cho mọi tài khoản đã đăng nhập
                        .requestMatchers("/notifications/**").authenticated()

                        // Mọi endpoint còn lại đều bắt buộc phải đăng nhập (có token hợp lệ)
                        .anyRequest().authenticated()
                )
                
                // Đăng ký JwtAuthenticationFilter trước UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

