package com.example.racehorse_transport.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.jackson.TypeNameResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public ModelConverter apiResponseSchemaNames() {
        return (type, context, chain) -> {
            JavaType model = type.getType() == null ? null : TypeFactory.defaultInstance().constructType(type.getType());
            if (model != null && model.hasRawClass(ApiResponseDto.class) && type.getName() == null) {
                // Each payload has its own published schema, e.g. ApiResponseBookingResponse.
                String generatedName = TypeNameResolver.std.nameForType(model);
                type.setName("ApiResponse" + generatedName.substring(ApiResponseDto.class.getSimpleName().length()));
            }
            return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        };
    }

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("RaceHorse Transport Management API")
                        .version("1.0.0")
                        .description("API Documentation cho he thong van chuyen ngua dua"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
