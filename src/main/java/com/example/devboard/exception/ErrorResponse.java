package com.example.devboard.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DevBoard 공통 오류 응답 DTO.
 *
 * 모든 오류 응답을 일관된 JSON 구조로 반환하기 위해 사용한다.
 *
 * 일반 오류:
 * - status, code, message, timestamp
 * - errors는 null
 *
 * 입력값 검증 오류:
 * - errors에 필드별 오류 메시지 포함
 */
@Schema(
        name = "ErrorResponse",
        description = "DevBoard API 공통 오류 응답"
)
public record ErrorResponse(

        @Schema(
                description = "HTTP 상태 코드",
                example = "400"
        )
        int status,

        @Schema(
                description = "애플리케이션 오류 식별 코드",
                example = "INVALID_INPUT"
        )
        String code,

        @Schema(
                description = "오류 설명 메시지",
                example = "잘못된 요청입니다."
        )
        String message,

        @Schema(
                description = "입력값 검증 실패 시 필드별 오류 메시지. 일반 오류에서는 null",
                example = "{\"email\":\"이메일 형식이 올바르지 않습니다.\"}",
                nullable = true
        )
        Map<String, String> errors,

        @Schema(
                description = "오류 발생 시각",
                example = "2026-11-18T18:30:00"
        )
        LocalDateTime timestamp
) {

    // =====================================================
    // 일반 오류 응답 생성
    // =====================================================

    /**
     * ErrorCode에 정의된 정보를 사용해 오류 응답을 생성한다.
     *
     * 예: POST_NOT_FOUND, MEMBER_NOT_FOUND 등
     */
    public static ErrorResponse from(ErrorCode errorCode) {

        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                LocalDateTime.now()
        );
    }

    // =====================================================
    // 입력값 검증 오류 응답 생성
    // =====================================================

    /**
     * @Valid 검증 실패 시 필드별 오류 정보를 반환한다.
     *
     * 예:
     * email -> 이메일 형식이 올바르지 않습니다.
     * password -> 비밀번호를 입력해야 합니다.
     */
    public static ErrorResponse validation(
            Map<String, String> errors
    ) {

        return new ErrorResponse(
                400,
                ErrorCode.INVALID_INPUT.getCode(),
                "입력값 검증에 실패했습니다.",
                errors,
                LocalDateTime.now()
        );
    }

    // =====================================================
    // 기존 예외와의 호환성을 위한 오류 응답 생성
    // =====================================================

    /**
     * HTTP 상태 코드, 오류 코드, 메시지를 직접 지정한다.
     *
     * 기존 예외 처리 로직과의 호환성을 위해 사용한다.
     */
    public static ErrorResponse of(
            int status,
            String code,
            String message
    ) {

        return new ErrorResponse(
                status,
                code,
                message,
                null,
                LocalDateTime.now()
        );
    }
}
