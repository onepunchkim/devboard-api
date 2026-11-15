package com.example.devboard.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 예외 상세 내용은 서버 로그에 기록
    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. 새로운 비즈니스 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception
    ) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    // 2. 기존 ResponseStatusException 처리
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException exception
    ) {
        int status = exception.getStatusCode().value();

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(ErrorResponse.of(
                        status,
                        "HTTP_" + status,
                        exception.getReason() != null
                                ? exception.getReason()
                                : "요청을 처리할 수 없습니다."
                ));
    }

    // 3. 회원 또는 게시글을 찾지 못한 경우
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        404,
                        "ENTITY_NOT_FOUND",
                        exception.getMessage() != null
                                ? exception.getMessage()
                                : "요청한 데이터를 찾을 수 없습니다."
                ));
    }

    // 4. @Valid 입력값 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        // 검증에 실패한 필드와 메시지 수집
        for (FieldError error :
                exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage()
            );
        }

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.validation(errors));
    }

    // 5. 파라미터 제약 조건 위반
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    // 6. 잘못된 JSON 요청
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(
                        400,
                        "INVALID_JSON",
                        "요청 본문이 올바른 JSON 형식이 아닙니다."
                ));
    }

    // 7. URL 경로 변수 또는 파라미터 타입 오류
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(
                        400,
                        "TYPE_MISMATCH",
                        "요청 파라미터의 형식이 올바르지 않습니다."
                ));
    }

    // 8. 로그인 인증 정보 오류
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(
                        401,
                        ErrorCode.INVALID_CREDENTIALS.getCode(),
                        "비밀번호가 올바르지 않습니다."
                ));
    }

    // 9. 중복 요청 또는 상태 충돌
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "CONFLICT",
                        exception.getMessage() != null
                                ? exception.getMessage()
                                : "현재 상태에서는 요청을 처리할 수 없습니다."
                ));
    }

    // 10. 예상하지 못한 서버 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception
    ) {
        // 상세 스택 트레이스는 서버 로그에만 기록
        log.error("처리되지 않은 서버 오류가 발생했습니다.", exception);

        // 클라이언트에는 내부 구현 정보를 노출하지 않음
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.from(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
    }
}
