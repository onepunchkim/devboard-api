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

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    // 테스트 대상: 전역 예외 처리기
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    // 공통 오류 응답의 HTTP 상태, 오류 코드, 메시지, 시간을 검증
    private void assertErrorResponse(
            ResponseEntity<ErrorResponse> response,
            HttpStatus expectedStatus,
            String expectedCode,
            String expectedMessage
    ) {
        // HTTP 응답 상태 확인
        assertEquals(expectedStatus, response.getStatusCode());

        ErrorResponse body = response.getBody();

        // 응답 본문이 존재하는지 확인
        assertNotNull(body);

        // 공통 오류 응답 필드 검증
        assertEquals(expectedStatus.value(), body.status());
        assertEquals(expectedCode, body.code());
        assertEquals(expectedMessage, body.message());

        // 오류 발생 시간이 포함되어 있는지 확인
        assertInstanceOf(LocalDateTime.class, body.timestamp());
    }

    // 1. ResponseStatusException의 HTTP 상태와 메시지 확인
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
                "HTTP_403",
                "접근 권한이 없습니다."
        );
    }

    // 2. 예외 메시지가 없을 때 기본 메시지 반환
    @Test
    void responseStatusException_withoutReason_shouldUseDefaultMessage() {
        ResponseStatusException exception =
                new ResponseStatusException(HttpStatus.BAD_REQUEST);

        assertErrorResponse(
                handler.handleResponseStatus(exception),
                HttpStatus.BAD_REQUEST,
                "HTTP_400",
                "요청을 처리할 수 없습니다."
        );
    }

    // 3. 존재하지 않는 데이터에 대해 HTTP 404 반환
    @Test
    void entityNotFound_shouldReturn404() {
        assertErrorResponse(
                handler.handleEntityNotFound(
                        new EntityNotFoundException(
                                "게시글을 찾을 수 없습니다."
                        )
                ),
                HttpStatus.NOT_FOUND,
                "ENTITY_NOT_FOUND",
                "게시글을 찾을 수 없습니다."
        );
    }

    // 4. 조회 예외에 메시지가 없을 때 기본 메시지 반환
    @Test
    void entityNotFound_withoutMessage_shouldUseDefaultMessage() {
        assertErrorResponse(
                handler.handleEntityNotFound(
                        new EntityNotFoundException()
                ),
                HttpStatus.NOT_FOUND,
                "ENTITY_NOT_FOUND",
                "요청한 데이터를 찾을 수 없습니다."
        );
    }

    // 5. 잘못된 JSON 요청에 대해 HTTP 400 반환
    @Test
    void invalidJson_shouldReturn400() {
        // JSON 파싱 오류를 나타내는 예외 생성
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException(
                        "Invalid JSON",
                        new MockHttpInputMessage(new byte[0])
                );

        assertErrorResponse(
                handler.handleInvalidJson(exception),
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "요청 본문이 올바른 JSON 형식이 아닙니다."
        );
    }

    // 6. 예상하지 못한 서버 오류에 대해 HTTP 500 반환
    @Test
    void unexpectedException_shouldReturn500() {
        assertErrorResponse(
                handler.handleUnexpected(
                        new RuntimeException("Internal error")
                ),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다."
        );
    }

    // 7. 로그인 인증 실패 시 HTTP 401 반환
    @Test
    void badCredentials_shouldReturn401() {
        assertErrorResponse(
                handler.handleBadCredentials(
                        new BadCredentialsException("Invalid password")
                ),
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "비밀번호가 올바르지 않습니다."
        );
    }

    // 8. 상태 충돌 시 HTTP 409 반환
    @Test
    void illegalState_shouldReturn409() {
        assertErrorResponse(
                handler.handleIllegalState(
                        new IllegalStateException(
                                "이미 좋아요를 등록했습니다."
                        )
                ),
                HttpStatus.CONFLICT,
                "CONFLICT",
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
                "CONFLICT",
                "현재 상태에서는 요청을 처리할 수 없습니다."
        );
    }

    // 10. 게시글을 찾을 수 없을 때 BusinessException 처리
    @Test
    void businessException_postNotFound_shouldReturn404() {
        BusinessException exception =
                new BusinessException(ErrorCode.POST_NOT_FOUND);

        assertErrorResponse(
                handler.handleBusinessException(exception),
                HttpStatus.NOT_FOUND,
                "POST_NOT_FOUND",
                "게시글을 찾을 수 없습니다."
        );
    }

    // 11. 게시글 수정 권한이 없을 때 HTTP 403 반환
    @Test
    void businessException_accessDenied_shouldReturn403() {
        BusinessException exception =
                new BusinessException(ErrorCode.POST_ACCESS_DENIED);

        assertErrorResponse(
                handler.handleBusinessException(exception),
                HttpStatus.FORBIDDEN,
                "POST_ACCESS_DENIED",
                "본인이 작성한 게시글만 수정하거나 삭제할 수 있습니다."
        );
    }

    // 12. 잘못된 페이지 요청에 대해 HTTP 400 반환
    @Test
    void businessException_invalidPage_shouldReturn400() {
        BusinessException exception =
                new BusinessException(ErrorCode.INVALID_PAGE);

        assertErrorResponse(
                handler.handleBusinessException(exception),
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE",
                "페이지 번호 또는 페이지 크기가 올바르지 않습니다."
        );
    }
}
