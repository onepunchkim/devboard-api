package com.example.devboard.controller;

import com.example.devboard.dto.request.MemberLoginRequest;
import com.example.devboard.dto.request.MemberSignupRequest;
import com.example.devboard.dto.request.MemberWithdrawalRequest;
import com.example.devboard.dto.response.MemberLoginResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.service.MemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@Tag(
        name = "회원 API",
        description = "회원가입, JWT 로그인 및 회원 탈퇴 API"
)
public class MemberController {

    private final MemberService memberService;

    // =====================================================
    // 생성자 주입
    // =====================================================

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // =====================================================
    // 1. 회원가입 API
    // =====================================================

    @Operation(
            summary = "회원가입",
            description = """
                    이메일, 비밀번호, 닉네임을 입력하여 회원가입합니다.

                    - JWT 인증이 필요하지 않습니다.
                    - 이메일은 중복으로 가입할 수 없습니다.
                    - 입력값은 Bean Validation으로 검증합니다.
                    - 회원가입 성공 시 비밀번호는 응답에 포함하지 않습니다.
                    """
    )

    // 회원가입은 JWT 인증이 필요하지 않음
    @SecurityRequirements()

    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = MemberSignupResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패(INVALID_INPUT)"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 가입된 이메일(DUPLICATE_EMAIL)"
            )
    })
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberSignupResponse signup(
            @Valid @RequestBody MemberSignupRequest request
    ) {

        // 회원가입 서비스 호출
        // 중복 이메일 발생 시 BusinessException이 전역 예외 처리기로 전달됨
        Member member = memberService.signup(request);

        // 비밀번호를 제외한 회원가입 결과 반환
        return new MemberSignupResponse(
                member.getId(),
                member.getEmail(),
                member.getNickname(),
                "회원가입이 완료되었습니다."
        );
    }

    // =====================================================
    // 2. 로그인 API
    // =====================================================

    @Operation(
            summary = "로그인",
            description = """
                    이메일과 비밀번호를 확인하고 JWT 토큰을 발급합니다.

                    - JWT 인증이 필요하지 않습니다.
                    - 로그인 성공 시 JWT 토큰을 반환합니다.
                    - 이메일 또는 비밀번호가 올바르지 않으면
                      401 Unauthorized 응답을 반환합니다.
                    """
    )

    // 로그인은 JWT 인증이 필요하지 않음
    @SecurityRequirements()

    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공 및 JWT 발급",
                    content = @Content(
                            schema = @Schema(
                                    implementation = MemberLoginResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패(INVALID_INPUT)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호 불일치(INVALID_CREDENTIALS)"
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
    // 3. 회원 탈퇴 API
    // =====================================================

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    로그인한 회원의 비밀번호를 확인한 후 계정을 삭제합니다.

                    - JWT 인증이 필요합니다.
                    - 현재 로그인한 회원만 자신의 계정을 탈퇴할 수 있습니다.
                    - 탈퇴 요청 시 비밀번호를 검증합니다.
                    - 비밀번호가 일치하지 않으면 401 오류를 반환합니다.
                    - 탈퇴 성공 시 204 No Content를 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )

    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "회원 탈퇴 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패(INVALID_INPUT)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 실패 또는 비밀번호 불일치(INVALID_CREDENTIALS)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원을 찾을 수 없음(MEMBER_NOT_FOUND)"
            )
    })
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(

            @Valid @RequestBody MemberWithdrawalRequest request,

            // 인증 객체는 Swagger 입력란에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // JWT에서 인증된 회원 이메일 조회
        String email = authentication.getName();

        // 회원 비밀번호 검증 후 탈퇴 처리
        memberService.withdraw(
                email,
                request
        );
    }

    // =====================================================
    // 4. 회원가입 성공 응답 DTO
    // =====================================================

    @Schema(
            name = "MemberSignupResponse",
            description = "회원가입 성공 시 반환하는 회원 정보"
    )
    public record MemberSignupResponse(

            @Schema(
                    description = "회원 ID",
                    example = "1"
            )
            Long id,

            @Schema(
                    description = "회원 이메일",
                    example = "user@example.com"
            )
            String email,

            @Schema(
                    description = "회원 닉네임",
                    example = "devboardUser"
            )
            String nickname,

            @Schema(
                    description = "회원가입 결과 메시지",
                    example = "회원가입이 완료되었습니다."
            )
            String message
    ) {
    }
}
