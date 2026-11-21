package com.example.devboard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalErrorOpenApiConfigTest {

    // 테스트 대상 설정 클래스
    private GlobalErrorOpenApiConfig config;

    // 테스트할 OpenAPI 커스터마이저
    private OpenApiCustomizer customizer;

    // 공통 오류 스키마 참조 경로
    private static final String ERROR_REF =
            "#/components/schemas/ErrorResponse";

    @BeforeEach
    void setUp() {

        // Spring Context 없이 설정 객체 생성
        config = new GlobalErrorOpenApiConfig();

        // @Bean 메서드가 반환하는 커스터마이저 획득
        customizer = config.globalErrorResponses();
    }

    // =====================================================
    // 1. Components가 없으면 자동 생성
    // =====================================================

    @Test
    void shouldCreateComponentsWhenMissing() {

        // given: Components가 없는 OpenAPI
        OpenAPI openAPI = new OpenAPI();

        assertNull(openAPI.getComponents());

        // when
        customizer.customise(openAPI);

        // then
        assertNotNull(openAPI.getComponents());

        assertNotNull(
                openAPI.getComponents()
                        .getSchemas()
                        .get("ErrorResponse")
        );
    }

    // =====================================================
    // 2. ErrorResponse 스키마 구조 검증
    // =====================================================

    @Test
    void shouldRegisterErrorResponseSchema() {

        // given
        OpenAPI openAPI = new OpenAPI();

        // when
        customizer.customise(openAPI);

        // then
        Schema<?> schema = openAPI.getComponents()
                .getSchemas()
                .get("ErrorResponse");

        assertNotNull(schema);

        // 공통 오류 응답은 object 타입
        assertEquals("object", schema.getType());

        // ErrorResponse 필드 확인
        Map<String, Schema> properties =
                schema.getProperties();

        assertNotNull(properties);

        assertTrue(properties.containsKey("status"));
        assertTrue(properties.containsKey("code"));
        assertTrue(properties.containsKey("message"));
        assertTrue(properties.containsKey("errors"));
        assertTrue(properties.containsKey("timestamp"));

        // 필드 타입 확인
        assertEquals(
                "integer",
                properties.get("status").getType()
        );

        assertEquals(
                "string",
                properties.get("code").getType()
        );

        assertEquals(
                "string",
                properties.get("message").getType()
        );

        // errors는 필드별 오류를 담는 객체
        Schema<?> errorsSchema = properties.get("errors");

        assertEquals("object", errorsSchema.getType());
        assertEquals(
                Boolean.TRUE,
                errorsSchema.getNullable()
        );

        assertTrue(
                errorsSchema.getAdditionalProperties()
                        instanceof Schema
        );

        // timestamp 형식 확인
        assertEquals(
                "date-time",
                properties.get("timestamp").getFormat()
        );
    }

    // =====================================================
    // 3. 기존 ErrorResponse 스키마는 덮어쓰지 않음
    // =====================================================

    @Test
    void shouldPreserveExistingErrorSchema() {

        // given: 이미 등록된 스키마
        Schema<?> existingSchema = new Schema<>()
                .type("object")
                .description("기존 오류 스키마");

        Components components = new Components()
                .addSchemas(
                        "ErrorResponse",
                        existingSchema
                );

        OpenAPI openAPI = new OpenAPI()
                .components(components);

        // when
        customizer.customise(openAPI);

        // then: 기존 객체가 그대로 유지되어야 함
        assertSame(
                existingSchema,
                openAPI.getComponents()
                        .getSchemas()
                        .get("ErrorResponse")
        );
    }

    // =====================================================
    // 4. Paths가 없어도 오류 없이 종료
    // =====================================================

    @Test
    void shouldHandleMissingPaths() {

        // given
        OpenAPI openAPI = new OpenAPI();

        // when & then
        assertDoesNotThrow(
                () -> customizer.customise(openAPI)
        );

        // API 경로가 없어도 공통 스키마는 등록
        assertNotNull(
                openAPI.getComponents()
                        .getSchemas()
                        .get("ErrorResponse")
        );
    }

    // =====================================================
    // 5. 기존 오류 응답에 공통 스키마 연결
    // =====================================================

    @Test
    void shouldAttachSchemaToErrorResponses() {

        // given: 오류 응답을 가진 API
        ApiResponses responses = new ApiResponses();

        for (String status : new String[]{
                "400", "401", "403", "404", "409", "500"
        }) {

            responses.addApiResponse(
                    status,
                    new ApiResponse()
                            .description("기존 오류 설명 " + status)
            );
        }

        OpenAPI openAPI = createOpenAPI(
                "/api/posts",
                responses
        );

        // when
        customizer.customise(openAPI);

        // then: 모든 오류 응답에 스키마 연결
        for (String status : new String[]{
                "400", "401", "403", "404", "409", "500"
        }) {

            ApiResponse response = responses.get(status);

            assertNotNull(response.getContent());

            MediaType json = response.getContent()
                    .get("application/json");

            assertNotNull(json);
            assertNotNull(json.getSchema());

            assertEquals(
                    ERROR_REF,
                    json.getSchema().get$ref()
            );

            // 기존 오류 설명은 유지
            assertEquals(
                    "기존 오류 설명 " + status,
                    response.getDescription()
            );
        }
    }

    // =====================================================
    // 6. 정상 응답은 수정하지 않음
    // =====================================================

    @Test
    void shouldPreserveSuccessResponses() {

        // given: 정상 응답과 오류 응답
        ApiResponse successResponse = new ApiResponse()
                .description("게시글 조회 성공");

        ApiResponse errorResponse = new ApiResponse()
                .description("잘못된 요청");

        ApiResponses responses = new ApiResponses()
                .addApiResponse("200", successResponse)
                .addApiResponse("400", errorResponse);

        OpenAPI openAPI = createOpenAPI(
                "/api/posts",
                responses
        );

        // when
        customizer.customise(openAPI);

        // then: 200 응답은 변경하지 않음
        assertSame(
                successResponse,
                responses.get("200")
        );

        assertNull(successResponse.getContent());

        assertEquals(
                "게시글 조회 성공",
                successResponse.getDescription()
        );

        // 400 응답에는 공통 스키마 연결
        assertEquals(
                ERROR_REF,
                errorResponse.getContent()
                        .get("application/json")
                        .getSchema()
                        .get$ref()
        );
    }

    // =====================================================
    // 7. 기존 Content가 있으면 유지
    // =====================================================

    @Test
    void shouldPreserveExistingContent() {

        // given: 기존 XML 응답 정보
        MediaType xmlMediaType = new MediaType();

        Content content = new Content()
                .addMediaType(
                        "application/xml",
                        xmlMediaType
                );

        ApiResponse errorResponse = new ApiResponse()
                .description("오류 발생")
                .content(content);

        ApiResponses responses = new ApiResponses()
                .addApiResponse("400", errorResponse);

        OpenAPI openAPI = createOpenAPI(
                "/api/posts",
                responses
        );

        // when
        customizer.customise(openAPI);

        // then: 기존 Content 객체 유지
        assertSame(
                content,
                errorResponse.getContent()
        );

        // 기존 XML MediaType 유지
        assertSame(
                xmlMediaType,
                content.get("application/xml")
        );

        // JSON MediaType은 새로 추가
        assertEquals(
                ERROR_REF,
                content.get("application/json")
                        .getSchema()
                        .get$ref()
        );
    }

    // =====================================================
    // 8. 기존 JSON MediaType이 있으면 재사용
    // =====================================================

    @Test
    void shouldReuseExistingJsonMediaType() {

        // given: 스키마가 없는 JSON MediaType
        MediaType existingJson = new MediaType();

        Content content = new Content()
                .addMediaType(
                        "application/json",
                        existingJson
                );

        ApiResponse errorResponse = new ApiResponse()
                .description("인증 실패")
                .content(content);

        ApiResponses responses = new ApiResponses()
                .addApiResponse("401", errorResponse);

        OpenAPI openAPI = createOpenAPI(
                "/api/members/me",
                responses
        );

        // when
        customizer.customise(openAPI);

        // then: 기존 MediaType 객체 유지
        assertSame(
                existingJson,
                errorResponse.getContent()
                        .get("application/json")
        );

        // 공통 오류 스키마 연결
        assertEquals(
                ERROR_REF,
                existingJson.getSchema().get$ref()
        );
    }

    // =====================================================
    // 9. 기존 JSON 스키마는 덮어쓰지 않음
    // =====================================================

    @Test
    void shouldPreserveExistingJsonSchema() {

        // given: 이미 별도 스키마가 지정된 응답
        Schema<?> existingSchema = new Schema<>()
                .$ref("#/components/schemas/CustomError");

        MediaType json = new MediaType()
                .schema(existingSchema);

        ApiResponse errorResponse = new ApiResponse()
                .description("커스텀 오류")
                .content(
                        new Content()
                                .addMediaType(
                                        "application/json",
                                        json
                                )
                );

        ApiResponses responses = new ApiResponses()
                .addApiResponse("409", errorResponse);

        OpenAPI openAPI = createOpenAPI(
                "/api/posts",
                responses
        );

        // when
        customizer.customise(openAPI);

        // then: 기존 스키마 유지
        assertSame(
                existingSchema,
                json.getSchema()
        );

        assertEquals(
                "#/components/schemas/CustomError",
                json.getSchema().get$ref()
        );
    }

    // =====================================================
    // 10. Responses가 null이면 건너뜀
    // =====================================================

    @Test
    void shouldHandleOperationWithoutResponses() {

        // given: 응답 정보가 없는 Operation
        Operation operation = new Operation();

        PathItem pathItem = new PathItem()
                .get(operation);

        OpenAPI openAPI = new OpenAPI()
                .paths(
                        new Paths().addPathItem(
                                "/api/empty",
                                pathItem
                        )
                );

        // when & then
        assertDoesNotThrow(
                () -> customizer.customise(openAPI)
        );

        // 공통 스키마 등록은 정상적으로 수행
        assertNotNull(
                openAPI.getComponents()
                        .getSchemas()
                        .get("ErrorResponse")
        );
    }

    // =====================================================
    // 11. 여러 경로와 HTTP 메서드 처리
    // =====================================================

    @Test
    void shouldProcessMultiplePathsAndOperations() {

        // given: GET API의 404 응답
        ApiResponse getError = new ApiResponse()
                .description("게시글 없음");

        Operation getOperation = new Operation()
                .responses(
                        new ApiResponses()
                                .addApiResponse(
                                        "404",
                                        getError
                                )
                );

        // POST API의 400 응답
        ApiResponse postError = new ApiResponse()
                .description("입력값 오류");

        Operation postOperation = new Operation()
                .responses(
                        new ApiResponses()
                                .addApiResponse(
                                        "400",
                                        postError
                                )
                );

        // DELETE API의 403 응답
        ApiResponse deleteError = new ApiResponse()
                .description("삭제 권한 없음");

        Operation deleteOperation = new Operation()
                .responses(
                        new ApiResponses()
                                .addApiResponse(
                                        "403",
                                        deleteError
                                )
                );

        Paths paths = new Paths()
                .addPathItem(
                        "/api/posts",
                        new PathItem()
                                .get(getOperation)
                                .post(postOperation)
                )
                .addPathItem(
                        "/api/posts/{id}",
                        new PathItem()
                                .delete(deleteOperation)
                );

        OpenAPI openAPI = new OpenAPI()
                .paths(paths);

        // when
        customizer.customise(openAPI);

        // then: 모든 오류 응답에 스키마 연결
        assertErrorSchemaReference(getError);
        assertErrorSchemaReference(postError);
        assertErrorSchemaReference(deleteError);
    }

    // =====================================================
    // 공통 테스트 유틸리티
    // =====================================================

    /**
     * 지정된 경로에 GET Operation을 가진 OpenAPI 생성
     */
    private OpenAPI createOpenAPI(
            String path,
            ApiResponses responses
    ) {

        Operation operation = new Operation()
                .responses(responses);

        PathItem pathItem = new PathItem()
                .get(operation);

        Paths paths = new Paths()
                .addPathItem(path, pathItem);

        return new OpenAPI().paths(paths);
    }

    /**
     * 오류 응답에 공통 ErrorResponse 스키마가
     * 연결되어 있는지 검증
     */
    private void assertErrorSchemaReference(
            ApiResponse response
    ) {

        assertNotNull(response.getContent());

        MediaType json = response.getContent()
                .get("application/json");

        assertNotNull(json);
        assertNotNull(json.getSchema());

        assertEquals(
                ERROR_REF,
                json.getSchema().get$ref()
        );
    }
}
