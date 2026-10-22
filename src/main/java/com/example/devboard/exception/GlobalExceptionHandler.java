package com.example.devboard.exception;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    //공통 오류 응답 생성
    private ResponseEntity<Map<String, Object>> createErrorResponse(
            HttpStatus status,
            String message
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(status).body(body);
    }

    //서비스에서 발생시킨 HTTP 상태 예외 처리
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException exception
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());

        return createErrorResponse(status, exception.getReason() != null ? exception.getReason() : "요청을 처리 할 수 없습니다.");
    }
    //회원 또는 게시글을 찾지 못한 경우
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(
            EntityNotFoundException exception
    ) {
        return createErrorResponse(HttpStatus.NOT_FOUND,
                exception.getMessage() != null ? exception.getMessage() : "요청한 데이터를 찾을 수 없습니다.");
    }

    //@Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors = new LinkedHashMap<>();

        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("message", "입력값 검증에 실패했습니다.");
        body.put("errors", errors);
        body.put("timestamp", LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    //JSON 형식이 잘못된 경우
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidation(
            HttpMessageNotReadableException exception
    ) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, "요청 본문이 올바른 JSON 형식이 아닙니다.");
    }

    //그 외 예상하지 못한 서버 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        //서버 로그에 상세 원인 기록
        exception.printStackTrace();

        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부에 오류가 발생했습니다.");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException exception) {
        return createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "비밀번호가 올바르지 않습니다."
        );
    }


    // 이미 처리된 요청 상태와 충돌하는 경우 처리
    // 예: 같은 회원이 같은 게시글에 좋아요를 중복 등록한 경우
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException exception
    ) {
        // 중복 요청은 409 Conflict 상태 코드로 반환
        return createErrorResponse(
                HttpStatus.CONFLICT,
                exception.getMessage() != null
                        ? exception.getMessage()
                        : "현재 상태에서는 요청을 처리할 수 없습니다."
        );
    }

}
