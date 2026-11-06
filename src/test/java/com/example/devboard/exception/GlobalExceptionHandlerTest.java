package com.example.devboard.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    // 공통 오류 응답의 상태, 메시지, 시간을 검증한다.
    private void assertErrorResponse(
            ResponseEntity<Map<String, Object>> response,
            HttpStatus expectedStatus,
            String expectedMessage
    ) {
        assertEquals(expectedStatus, response.getStatusCode());

        Map<String, Object> body = response.getBody();

        assertNotNull(body);
        assertEquals(expectedStatus.value(), body.get("status"));
        assertEquals(expectedMessage, body.get("message"));

        // 오류 응답에 발생 시각이 포함되어 있는지 확인
        assertInstanceOf(LocalDateTime.class, body.get("timestamp"));
    }

    // 1. 지정된 HTTP 상태와 메시지 반환
    @Test
    void responseStatusException_shouldReturnSpecifiedStatus() {
        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "접근 권한이 없습니다."
                );

        assertErrorResponse(
                handler.handleResponseStatus(exception),
                HttpStatus.FORBIDDEN,
                "접근 권한이 없습니다."
        );
    }

    // 2. 예외 메시지가 없으면 기본 메시지 반환
    @Test
    void responseStatusException_withoutReason_shouldUseDefaultMessage() {
        ResponseStatusException exception =
                new ResponseStatusException(HttpStatus.BAD_REQUEST);

        assertErrorResponse(
                handler.handleResponseStatus(exception),
                HttpStatus.BAD_REQUEST,
                "요청을 처리 할 수 없습니다."
        );
    }

    // 3. 존재하지 않는 데이터에 대해 404 반환
    @Test
    void entityNotFound_shouldReturn404() {
        assertErrorResponse(
                handler.handleEntityNotFound(
                        new EntityNotFoundException("게시글을 찾을 수 없습니다.")
                ),
                HttpStatus.NOT_FOUND,
                "게시글을 찾을 수 없습니다."
        );
    }

    // 4. 데이터 조회 예외에 메시지가 없으면 기본 메시지 반환
    @Test
    void entityNotFound_withoutMessage_shouldUseDefaultMessage() {
        assertErrorResponse(
                handler.handleEntityNotFound(
                        new EntityNotFoundException()
                ),
                HttpStatus.NOT_FOUND,
                "요청한 데이터를 찾을 수 없습니다."
        );
    }

    // 5. 잘못된 JSON 요청에 대해 400 반환
    @Test
    void invalidJson_shouldReturn400() {

        // given: JSON 파싱 오류를 나타내는 예외 생성
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException(
                        "Invalid JSON",
                        new MockHttpInputMessage(new byte[0])
                );

        // when & then: HTTP 400 및 오류 메시지 검증
        assertErrorResponse(
                handler.handleInvalidation(exception),
                HttpStatus.BAD_REQUEST,
                "요청 본문이 올바른 JSON 형식이 아닙니다."
        );
    }


    // 6. 예상하지 못한 오류에 대해 500 반환
    @Test
    void unexpectedException_shouldReturn500() {
        assertErrorResponse(
                handler.handleUnexpected(
                        new RuntimeException("Internal error")
                ),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 내부에 오류가 발생했습니다."
        );
    }

    // 7. 인증 실패 시 401 반환
    @Test
    void badCredentials_shouldReturn401() {
        assertErrorResponse(
                handler.handleBadCredentials(
                        new BadCredentialsException("Invalid password")
                ),
                HttpStatus.UNAUTHORIZED,
                "비밀번호가 올바르지 않습니다."
        );
    }

    // 8. 상태 충돌 시 409 반환
    @Test
    void illegalState_shouldReturn409() {
        assertErrorResponse(
                handler.handleIllegalState(
                        new IllegalStateException("이미 좋아요를 등록했습니다.")
                ),
                HttpStatus.CONFLICT,
                "이미 좋아요를 등록했습니다."
        );
    }

    // 9. 상태 충돌 메시지가 없으면 기본 메시지 반환
    @Test
    void illegalState_withoutMessage_shouldUseDefaultMessage() {
        assertErrorResponse(
                handler.handleIllegalState(
                        new IllegalStateException()
                ),
                HttpStatus.CONFLICT,
                "현재 상태에서는 요청을 처리할 수 없습니다."
        );
    }
}
