package com.example.devboard.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // =====================================================
    // 1. 공통 오류
    // =====================================================

    // 요청 데이터가 올바르지 않은 경우
    INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "INVALID_INPUT",
            "입력값이 올바르지 않습니다."
    ),

    // 서버 내부에서 예상하지 못한 오류가 발생한 경우
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "서버 내부 오류가 발생했습니다."
    ),

    // =====================================================
    // 2. 회원 관련 오류
    // =====================================================

    // 요청한 회원을 찾을 수 없는 경우
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER_NOT_FOUND",
            "회원을 찾을 수 없습니다."
    ),

    // 로그인 이메일 또는 비밀번호가 올바르지 않은 경우
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    // 이미 가입된 이메일로 회원가입을 시도한 경우
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "DUPLICATE_EMAIL",
            "이미 사용 중인 이메일입니다."
    ),

    // =====================================================
    // 3. 게시글 관련 오류
    // =====================================================

    // 요청한 게시글을 찾을 수 없는 경우
    POST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "POST_NOT_FOUND",
            "게시글을 찾을 수 없습니다."
    ),

    // 게시글 작성자가 아닌 사용자가 수정 또는 삭제를 시도한 경우
    POST_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "POST_ACCESS_DENIED",
            "본인이 작성한 게시글만 수정하거나 삭제할 수 있습니다."
    ),

    // =====================================================
    // 4. 페이지 및 정렬 관련 오류
    // =====================================================

    // 페이지 번호 또는 페이지 크기가 허용 범위를 벗어난 경우
    INVALID_PAGE(
            HttpStatus.BAD_REQUEST,
            "INVALID_PAGE",
            "페이지 번호 또는 페이지 크기가 올바르지 않습니다."
    ),

    // 지원하지 않는 게시글 정렬 조건을 요청한 경우
    INVALID_SORT(
            HttpStatus.BAD_REQUEST,
            "INVALID_SORT",
            "지원하지 않는 정렬 조건입니다."
    ),

    // =====================================================
    // 5. 좋아요 관련 오류
    // =====================================================

    // 동일한 회원이 같은 게시글에 중복으로 좋아요를 등록한 경우
    LIKE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "LIKE_ALREADY_EXISTS",
            "이미 좋아요를 누른 게시글입니다."
    ),

    // 좋아요 취소 요청 시 해당 회원의 좋아요 기록이 없는 경우
    LIKE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "LIKE_NOT_FOUND",
            "좋아요 기록을 찾을 수 없습니다."
    ),

    // =====================================================
    // 6. 댓글 관련 오류 (신규 추가)
    // =====================================================

    // 요청한 댓글이 존재하지 않는 경우
    COMMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMENT_NOT_FOUND",
            "댓글을 찾을 수 없습니다."
    ),

    // 댓글 작성자가 아닌 사용자가 수정 또는 삭제를 시도한 경우
    COMMENT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "COMMENT_ACCESS_DENIED",
            "댓글 작성자만 수정하거나 삭제할 수 있습니다."
    );

    // =====================================================
    // 오류 코드 공통 필드
    // =====================================================

    // 클라이언트에 반환할 HTTP 상태 코드
    private final HttpStatus status;

    // 오류를 구분하기 위한 문자열 코드
    private final String code;

    // 사용자에게 전달할 오류 메시지
    private final String message;

    // =====================================================
    // 생성자
    // =====================================================

    ErrorCode(
            HttpStatus status,
            String code,
            String message
    ) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    // =====================================================
    // Getter
    // =====================================================

    // HTTP 상태 코드 반환
    public HttpStatus getStatus() {
        return status;
    }

    // 문자열 오류 코드 반환
    public String getCode() {
        return code;
    }

    // 오류 메시지 반환
    public String getMessage() {
        return message;
    }
}
