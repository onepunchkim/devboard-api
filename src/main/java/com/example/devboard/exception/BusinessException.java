package com.example.devboard.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    // 발생한 비즈니스 오류의 종류
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        // RuntimeException에 오류 메시지 전달
        super(errorCode.getMessage());

        // HTTP 상태와 오류 코드를 함께 보관
        this.errorCode = errorCode;
    }

}
