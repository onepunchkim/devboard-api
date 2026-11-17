package com.example.devboard.controller;

import com.example.devboard.repository.MemberRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/members")

@Tag(
        name = "온라인 회원 API",
        description = "WebSocket 연결 상태를 기반으로 현재 접속 중인 회원을 조회하는 API"
)
public class OnlineMemberController {

    private final MemberRepository memberRepository;
    private final SimpUserRegistry simpUserRegistry;

    // 필요한 Repository와 WebSocket 사용자 관리 객체 주입
    public OnlineMemberController(
            MemberRepository memberRepository,
            SimpUserRegistry simpUserRegistry
    ) {
        this.memberRepository = memberRepository;
        this.simpUserRegistry = simpUserRegistry;
    }

    // =====================================================
    // 현재 온라인 상태인 다른 회원 목록 조회
    // =====================================================

    @Operation(
            summary = "온라인 회원 목록 조회",
            description = """
                    현재 WebSocket/STOMP 연결이 유지되는 회원 목록을 조회합니다.

                    - JWT 인증이 필요합니다.
                    - 현재 로그인한 회원은 결과에서 제외합니다.
                    - 연결된 회원의 이메일로 DB에서 회원 정보를 조회합니다.
                    - 회원 ID와 닉네임만 반환합니다.
                    - 닉네임을 기준으로 정렬합니다.

                    주의: 온라인 상태는 STOMP 연결 정보를 기준으로 판단하므로
                    실제 접속 상태와 일시적인 차이가 발생할 수 있습니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "온라인 회원 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = OnlineMemberResponse.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            )
    })
    @GetMapping("/online")
    public List<OnlineMemberResponse> getOnlineMembers(

            // 인증 정보는 Swagger 파라미터 목록에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // 현재 로그인한 회원의 이메일 조회
        String currentEmail = authentication.getName();

        return simpUserRegistry.getUsers()
                .stream()

                // STOMP 연결 시 인증된 회원의 이메일 조회
                .map(SimpUser::getName)

                // 현재 로그인한 회원은 목록에서 제외
                .filter(email ->
                        !Objects.equals(email, currentEmail)
                )

                // 이메일을 기준으로 DB에서 회원 조회
                .map(memberRepository::findByEmail)

                // 실제 존재하는 회원만 목록에 포함
                .flatMap(java.util.Optional::stream)

                // 개인정보를 제외한 응답 DTO 생성
                .map(member -> new OnlineMemberResponse(
                        member.getId(),
                        member.getNickname()
                ))

                // 닉네임 기준 정렬 (대소문자 구분 없음)
                .sorted(Comparator.comparing(
                        OnlineMemberResponse::nickname,
                        Comparator.nullsLast(
                                String.CASE_INSENSITIVE_ORDER
                        )
                ))

                // 최종 온라인 회원 목록 반환
                .toList();
    }

    // =====================================================
    // 온라인 회원 조회 응답 DTO
    // =====================================================

    /**
     * 온라인 회원의 공개 정보만 반환한다.
     *
     * 이메일, 비밀번호 등 불필요한 개인정보는 제외한다.
     */
    @Schema(description = "온라인 회원 정보")
    public record OnlineMemberResponse(

            @Schema(
                    description = "회원 ID",
                    example = "2"
            )
            Long memberId,

            @Schema(
                    description = "회원 닉네임",
                    example = "user2"
            )
            String nickname
    ) {}
}
