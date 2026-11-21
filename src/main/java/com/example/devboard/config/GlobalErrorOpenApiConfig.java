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
import java.util.Set;

@Configuration
public class GlobalErrorOpenApiConfig {

    // 공통 오류 응답에 해당하는 HTTP 상태 코드
    private static final Set<String> ERROR_STATUS_CODES = Set.of(
            "400",
            "401",
            "403",
            "404",
            "409",
            "500"
    );

    // ErrorResponse 공통 스키마 참조 경로
    private static final String ERROR_SCHEMA_REF =
            "#/components/schemas/ErrorResponse";

    /**
     * Swagger/OpenAPI 문서에 공통 오류 응답 스키마를 연결한다.
     *
     * 1. ErrorResponse 스키마 등록
     * 2. 기존 API 오류 응답에 스키마 연결
     * 3. 기존 설명과 정상 응답 유지
     *
     * 실제 HTTP 예외 처리 동작은 변경하지 않는다.
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

            // =====================================================
            // ErrorResponse 스키마 등록
            // =====================================================

            // Components에 schemas가 아직 생성되지 않았을 수 있으므로
            // null 여부를 먼저 확인한다.
            if (components.getSchemas() == null
                    || !components.getSchemas().containsKey("ErrorResponse")) {

                // 기존 ErrorResponse 스키마가 없을 때만 등록
                components.addSchemas(
                        "ErrorResponse",
                        createErrorSchema()
                );
            }

            // =================================================
            // 2. API별 기존 오류 응답에 스키마 연결
            // =================================================

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().values().forEach(pathItem ->

                    pathItem.readOperations().forEach(operation -> {

                        ApiResponses responses =
                                operation.getResponses();

                        if (responses == null) {
                            return;
                        }

                        // 기존에 선언된 오류 응답만 검사
                        responses.forEach((statusCode, response) -> {

                            // 공통 오류 응답 대상이 아니면 건너뜀
                            if (!ERROR_STATUS_CODES.contains(statusCode)) {
                                return;
                            }

                            // 기존 오류 응답에 JSON 스키마 연결
                            attachErrorSchema(response);
                        });
                    })
            );
        };
    }

    // =====================================================
    // 공통 오류 응답 스키마 생성
    // =====================================================

    /**
     * ErrorResponse record와 동일한 필드 구조를 정의한다.
     */
    private Schema<?> createErrorSchema() {

        Schema<Object> errorSchema = new Schema<>();

        errorSchema.setType("object");
        errorSchema.setDescription(
                "DevBoard API 공통 오류 응답"
        );

        // HTTP 상태 코드
        errorSchema.addProperty(
                "status",
                new Schema<Integer>()
                        .type("integer")
                        .example(400)
                        .description("HTTP 상태 코드")
        );

        // 애플리케이션 오류 코드
        errorSchema.addProperty(
                "code",
                new Schema<String>()
                        .type("string")
                        .example("INVALID_INPUT")
                        .description("오류 식별 코드")
        );

        // 사용자에게 전달할 오류 메시지
        errorSchema.addProperty(
                "message",
                new Schema<String>()
                        .type("string")
                        .example("잘못된 요청입니다.")
                        .description("오류 메시지")
        );

        // 필드별 입력값 검증 오류
        Schema<String> errorValueSchema = new Schema<>();
        errorValueSchema.setType("string");

        Schema<Map<String, String>> errorsSchema =
                new Schema<>();

        errorsSchema.setType("object");
        errorsSchema.setDescription(
                "입력값 검증 실패 시 필드별 오류 메시지. " +
                        "일반 오류에서는 null"
        );
        errorsSchema.setAdditionalProperties(errorValueSchema);
        errorsSchema.setNullable(true);

        errorSchema.addProperty(
                "errors",
                errorsSchema
        );

        // 오류 발생 시각
        errorSchema.addProperty(
                "timestamp",
                new Schema<String>()
                        .type("string")
                        .format("date-time")
                        .example("2026-11-18T18:30:00")
                        .description("오류 발생 시각")
        );

        return errorSchema;
    }

    // =====================================================
    // 기존 오류 응답에 공통 스키마 연결
    // =====================================================

    /**
     * 이미 선언된 오류 응답의 application/json에
     * ErrorResponse 스키마를 연결한다.
     *
     * 기존 설명, 예시, 정상 응답은 유지한다.
     */
    private void attachErrorSchema(ApiResponse response) {

        // 기존 응답에 Content가 없다면 생성
        Content content = response.getContent();

        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        // application/json 응답이 없다면 생성
        io.swagger.v3.oas.models.media.MediaType mediaType =
                content.get("application/json");

        if (mediaType == null) {

            mediaType =
                    new io.swagger.v3.oas.models.media.MediaType();

            content.addMediaType(
                    "application/json",
                    mediaType
            );
        }

        // 이미 지정된 스키마는 덮어쓰지 않음
        if (mediaType.getSchema() != null) {
            return;
        }

        // 공통 오류 응답 스키마 참조
        Schema<?> schema = new Schema<>()
                .$ref(ERROR_SCHEMA_REF);

        mediaType.setSchema(schema);
    }
}
