package com.example.devboard.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 게시글 생성 및 수정 요청 데이터 검증
public record PostRequest(

        // 제목은 필수이며 최대 200자
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        // 내용은 비어 있을 수 없음
        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}
