package com.example.devboard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI devBoardOpenAPI() {

        // Swagger에서 사용할 JWT 인증 방식 정의
        String securitySchemeName = "bearerAuth";

        return new OpenAPI()

                // API 문서 기본 정보
                .info(new Info()
                        .title("DevBoard REST API")
                        .description(
                                "Spring Boot 기반 게시판 서비스 API 문서"
                        )
                        .version("1.0.0")
                )

                // JWT Bearer 인증 설정
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(securitySchemeName)
                )

                .schemaRequirement(
                        securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                );
    }
}
