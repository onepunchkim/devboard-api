package com.example.devboard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class GlobalErrorOpenApiConfig {

    /**
     * Swagger 문서에 공통 오류 응답을 등록한다.
     *
     * 실제 예외 처리는 GlobalExceptionHandler가 담당한다.
     * 이 설정은 API 문서만 변경한다.
     */
    @Bean
    public OpenApiCustomizer globalErrorResponses() {

        return openApi -> {

            // =================================================
            // 1. 공통 오류 응답 스키마 등록
            // =================================================

            Components components = openApi.getComponents();

            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }

            // ErrorResponse DTO를 공통 스키마로 등록
            Schema<?> errorSchema = new Schema<>()
                    .type("object")
                    .description("DevBoard 공통 오류 응답");

            errorSchema.addProperty(
                    "status",
                    new Schema<Integer>()
                            .type("integer")
                            .example(400)
                            .description("HTTP 상태 코드")
            );

            errorSchema.addProperty(
                    "code",
                    new Schema<String>()
                            .type("string")
                            .example("INVALID_INPUT")
                            .description("오류 식별 코드")
            );

            errorSchema.addProperty(
                    "message",
                    new Schema<String>()
                            .type("string")
                            .example("잘못된 요청입니다.")
                            .description("오류 메시지")
            );

            errorSchema.addProperty(
                    "errors",
                    new Schema<Map<String, String>>()
                            .type("object")
                            .description("입력값 검증 오류 상세 정보")
            );

            errorSchema.addProperty(
                    "timestamp",
                    new Schema<String>()
                            .type("string")
                            .format("date-time")
                            .description("오류 발생 시각")
            );

            components.addSchemas(
                    "ErrorResponse",
                    errorSchema
            );

            // =================================================
            // 2. API별 공통 오류 응답 등록
            // =================================================

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().values().forEach(pathItem ->

                    pathItem.readOperations().forEach(operation -> {

                        ApiResponses responses = operation.getResponses();

                        if (responses == null) {
                            responses = new ApiResponses();
                            operation.setResponses(responses);
                        }

                        // 기존 API 문서에 등록된 응답은 유지한다.
                        // 없는 응답만 추가한다.
                        addErrorResponse(
                                responses,
                                "400",
                                "잘못된 요청"
                        );

                        addErrorResponse(
                                responses,
                                "401",
                                "인증이 필요합니다."
                        );

                        addErrorResponse(
                                responses,
                                "403",
                                "접근 권한이 없습니다."
                        );

                        addErrorResponse(
                                responses,
                                "404",
                                "요청한 데이터를 찾을 수 없습니다."
                        );

                        addErrorResponse(
                                responses,
                                "409",
                                "요청 상태 충돌"
                        );

                        addErrorResponse(
                                responses,
                                "500",
                                "서버 내부 오류"
                        );
                    })
            );
        };
    }

    // =====================================================
    // 공통 오류 응답 생성
    // =====================================================

    private void addErrorResponse(
            ApiResponses responses,
            String statusCode,
            String description
    ) {

        // 이미 등록된 HTTP 응답은 덮어쓰지 않음
        if (responses.containsKey(statusCode)) {
            return;
        }

        // ErrorResponse 공통 스키마 참조
        Schema<?> schema = new Schema<>()
                .$ref("#/components/schemas/ErrorResponse");

        ApiResponse response = new ApiResponse()
                .description(description)
                .content(
                        new Content().addMediaType(
                                "application/json",
                                new io.swagger.v3.oas.models.media.MediaType()
                                        .schema(schema)
                        )
                );

        responses.addApiResponse(
                statusCode,
                response
        );
    }
}
