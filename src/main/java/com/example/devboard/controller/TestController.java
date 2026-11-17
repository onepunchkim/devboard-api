package com.example.devboard.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

@Tag(
        name = "서버 테스트 API",
        description = "DevBoard API 서버의 기본 동작을 확인하는 API"
)
public class TestController {

    // =====================================================
    // 서버 동작 확인
    // =====================================================

    @Operation(
            summary = "API 서버 동작 확인",
            description = "DevBoard API 서버가 정상적으로 요청에 응답하는지 확인합니다."
    )

    // Swagger 전역 JWT 요구사항을 이 API에서 해제
    // 실제 인증 허용 여부는 SecurityConfig에서 결정됨
    @SecurityRequirements()

    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "서버 정상 응답",
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN_VALUE,
                            schema = @Schema(
                                    type = "string",
                                    example = "Devboard API 서버가 정상적으로 동작합니다."
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증이 필요한 경우"
            )
    })
    @GetMapping(
            value = "/api/test",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String test() {

        // 서버 정상 동작 확인용 메시지 반환
        return "Devboard API 서버가 정상적으로 동작합니다.";
    }
}
