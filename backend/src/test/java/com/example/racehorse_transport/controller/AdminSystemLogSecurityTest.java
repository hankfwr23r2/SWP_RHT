package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.service.AdminAuditWriter;
import com.example.racehorse_transport.service.AdminSystemLogService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminSystemLogSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean AdminSystemLogService service() { return mock(AdminSystemLogService.class); }
        @Bean AdminSystemLogController controller(AdminSystemLogService service) { return new AdminSystemLogController(service); }
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void onlyAdminCanListReadDetailsOrLoadOptions() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(AdminSystemLogController.class);
            var service = context.getBean(AdminSystemLogService.class);
            for (String role : List.of("MANAGER", "COORDINATOR", "SPECIALIST", "DRIVER", "ESCORT", "CUSTOMER")) {
                signIn(role);
                assertThatThrownBy(controller::options).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controller.detail(1)).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controller.search(null, null, null, null, null, null, 0, 20, null))
                        .isInstanceOf(AccessDeniedException.class);
            }
            verifyNoInteractions(service);
            signIn("ADMIN");
            assertThat(controller.options().getData()).isEqualTo(AdminAuditWriter.ACTIONS);
            controller.detail(1);
            controller.search(null, null, null, null, null, null, 0, 20, null);
            verify(service).detail(1);
            verify(service).search(null, null, null, null, null, null, 0, 20, null);
        }
    }

    private void signIn(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
