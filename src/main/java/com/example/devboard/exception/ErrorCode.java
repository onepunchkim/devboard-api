package com.example.devboard.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 공통 오류
    INVALID_INPUT(HttpStatus.BAD_REQUEST,
            "INVALID_INPUT", "잘못된 요청입니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    // 회원 관련 오류
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND,
            "MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS", "로그인 정보가 올바르지 않습니다."),

    DUPLICATE_EMAIL(HttpStatus.CONFLICT,
            "DUPLICATE_EMAIL", "이미 가입된 이메일입니다."),

    // 게시글 관련 오류
    POST_NOT_FOUND(HttpStatus.NOT_FOUND,
            "POST_NOT_FOUND", "게시글을 찾을 수 없습니다."),

    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN,
            "POST_ACCESS_DENIED",
            "본인이 작성한 게시글만 수정하거나 삭제할 수 있습니다."),

    INVALID_PAGE(HttpStatus.BAD_REQUEST,
            "INVALID_PAGE", "페이지 번호 또는 페이지 크기가 올바르지 않습니다."),

    INVALID_SORT(HttpStatus.BAD_REQUEST,
            "INVALID_SORT", "지원하지 않는 정렬 조건입니다.");

    // HTTP 상태 코드
    private final HttpStatus status;

    // 프론트엔드에서 식별할 오류 코드
    private final String code;

    // 사용자에게 전달할 오류 메시지
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

}
