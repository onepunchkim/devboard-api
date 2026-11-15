package com.example.devboard.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(
        int status,
        String code,
        String message,
        Map<String, String> errors,
        LocalDateTime timestamp
) {

    // 일반 오류 응답 생성
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                LocalDateTime.now()
        );
    }

    // 입력값 검증 오류 응답 생성
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

    // 기존 예외와의 호환성을 위한 응답 생성
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
