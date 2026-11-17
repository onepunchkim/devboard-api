package com.example.devboard.controller;

import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.request.MemberWithdrawalRequest;
import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.response.MemberLoginResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.service.MemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/members")

@Tag(
        name = "회원 API",
        description = "회원가입, 로그인 및 회원 탈퇴 API"
)
public class MemberController {

    private final MemberService memberService;

    // 회원 서비스 생성자 주입
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // =====================================================
    // 회원가입 API
    // =====================================================

    @Operation(
            summary = "회원가입",
            description = "이메일, 비밀번호, 닉네임을 입력하여 회원가입합니다."
    )

    // 회원가입은 JWT 인증이 필요하지 않음
    @SecurityRequirements()

    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 가입된 이메일"
            )
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(
            @Valid @RequestBody MemberSignupRequest request
    ) {

        try {
            Member member = memberService.signup(request);

            // 비밀번호는 응답에 포함하지 않음
            return Map.of(
                    "id", member.getId(),
                    "email", member.getEmail(),
                    "nickname", member.getNickname(),
                    "message", "회원가입이 완료되었습니다."
            );

        } catch (IllegalArgumentException e) {

            // 기존 중복 이메일 예외 처리 유지
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    e.getMessage()
            );
        }
    }

    // =====================================================
    // 로그인 API
    // =====================================================

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 확인하고 JWT 토큰을 발급합니다."
    )

    // 로그인은 JWT 인증이 필요하지 않음
    @SecurityRequirements()

    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공 및 JWT 발급"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호가 올바르지 않음"
            )
    })
    @PostMapping("/login")
    public MemberLoginResponse login(
            @Valid @RequestBody MemberLoginRequest request
    ) {

        // 회원 인증 후 JWT 토큰 응답
        return memberService.login(request);
    }

    // =====================================================
    // 회원 탈퇴 API
    // =====================================================

    @Operation(
            summary = "회원 탈퇴",
            description = "로그인한 회원의 비밀번호를 확인한 후 계정을 삭제합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "회원 탈퇴 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 실패 또는 비밀번호 불일치"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원을 찾을 수 없음"
            )
    })
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @Valid @RequestBody MemberWithdrawalRequest request,

            // Spring Security에서 전달하는 인증 정보는 문서에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 인증된 회원 이메일과 탈퇴 요청 데이터를 서비스로 전달
        memberService.withdraw(
                authentication.getName(),
                request
        );
    }
}
