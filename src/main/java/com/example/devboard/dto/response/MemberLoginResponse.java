package com.example.devboard.dto.response;

//로그인 응답
public record MemberLoginResponse(
        Long memberId,
        String email,
        String nickname,
        String accessToken,
        String tokenType
) {
}
