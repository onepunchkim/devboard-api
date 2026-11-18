package com.example.devboard.controller;

import com.example.devboard.service.PostLikeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/like")
@Tag(
        name = "좋아요 API",
        description = "게시글 좋아요 등록, 취소 및 좋아요 정보 조회 API"
)
public class PostLikeController {

    private final PostLikeService postLikeService;

    // =====================================================
    // 생성자 주입
    // =====================================================

    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    // =====================================================
    // 1. 게시글 좋아요 등록
    // =====================================================

    @Operation(
            summary = "게시글 좋아요 등록",
            description = """
                    로그인한 사용자가 특정 게시글에 좋아요를 등록합니다.

                    - JWT 인증이 필요합니다.
                    - 동일한 게시글에 중복 좋아요를 등록할 수 없습니다.
                    - 중복 등록 시 LIKE_ALREADY_EXISTS 오류를 반환합니다.
                    - 존재하지 않는 게시글은 POST_NOT_FOUND 오류를 반환합니다.
                    - 존재하지 않는 회원은 MEMBER_NOT_FOUND 오류를 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "좋아요 등록 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = LikeActionResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류(TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원(MEMBER_NOT_FOUND) 또는 게시글(POST_NOT_FOUND)을 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 좋아요를 등록함(LIKE_ALREADY_EXISTS)"
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LikeActionResponse likePost(

            @Parameter(
                    description = "좋아요를 등록할 게시글 ID",
                    example = "1",
                    required = true
            )
            @PathVariable Long postId,

            // Spring Security 인증 객체는 Swagger 입력란에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // JWT 인증 필터에서 설정한 회원 이메일 조회
        String email = authentication.getName();

        // 회원과 게시글 존재 여부 및 중복 좋아요 여부 검증 후 등록
        postLikeService.likePostByEmail(email, postId);

        // 좋아요 등록 성공 응답
        return new LikeActionResponse(
                postId,
                true,
                "좋아요를 등록했습니다."
        );
    }

    // =====================================================
    // 2. 게시글 좋아요 취소
    // =====================================================

    @Operation(
            summary = "게시글 좋아요 취소",
            description = """
                    로그인한 사용자가 등록한 게시글 좋아요를 취소합니다.

                    - JWT 인증이 필요합니다.
                    - 좋아요 기록이 없는 경우 LIKE_NOT_FOUND 오류를 반환합니다.
                    - 존재하지 않는 게시글은 POST_NOT_FOUND 오류를 반환합니다.
                    - 존재하지 않는 회원은 MEMBER_NOT_FOUND 오류를 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "좋아요 취소 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = LikeActionResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류(TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원(MEMBER_NOT_FOUND), 게시글(POST_NOT_FOUND) 또는 좋아요 기록(LIKE_NOT_FOUND)을 찾을 수 없음"
            )
    })
    @DeleteMapping
    public LikeActionResponse unlikePost(

            @Parameter(
                    description = "좋아요를 취소할 게시글 ID",
                    example = "1",
                    required = true
            )
            @PathVariable Long postId,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 현재 로그인한 회원 이메일 조회
        String email = authentication.getName();

        // 해당 회원의 좋아요 기록 확인 후 삭제
        postLikeService.unlikePostByEmail(email, postId);

        // 좋아요 취소 성공 응답
        return new LikeActionResponse(
                postId,
                false,
                "좋아요를 취소했습니다."
        );
    }

    // =====================================================
    // 3. 게시글 좋아요 정보 조회
    // =====================================================

    @Operation(
            summary = "게시글 좋아요 정보 조회",
            description = """
                    게시글의 전체 좋아요 수와 로그인한 사용자의 좋아요 여부를 조회합니다.

                    - JWT 인증이 필요합니다.
                    - likeCount는 전체 좋아요 개수입니다.
                    - liked는 현재 로그인한 사용자의 좋아요 여부입니다.
                    - 존재하지 않는 게시글은 POST_NOT_FOUND 오류를 반환합니다.
                    - 존재하지 않는 회원은 MEMBER_NOT_FOUND 오류를 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "좋아요 정보 조회 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = LikeInfoResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류(TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "회원(MEMBER_NOT_FOUND) 또는 게시글(POST_NOT_FOUND)을 찾을 수 없음"
            )
    })
    @GetMapping
    public LikeInfoResponse getLikeInfo(

            @Parameter(
                    description = "좋아요 정보를 조회할 게시글 ID",
                    example = "1",
                    required = true
            )
            @PathVariable Long postId,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 현재 로그인한 회원 이메일 조회
        String email = authentication.getName();

        // 게시글 전체 좋아요 개수 조회
        long likeCount = postLikeService.getLikeCount(postId);

        // 로그인한 회원이 해당 게시글에 좋아요를 등록했는지 확인
        boolean liked = postLikeService.hasLikedByEmail(
                email,
                postId
        );

        // 좋아요 개수 및 현재 회원의 좋아요 상태 반환
        return new LikeInfoResponse(
                postId,
                likeCount,
                liked
        );
    }

    // =====================================================
    // 4. 좋아요 등록 및 취소 응답 DTO
    // =====================================================

    @Schema(
            name = "LikeActionResponse",
            description = "게시글 좋아요 등록 및 취소 결과"
    )
    public record LikeActionResponse(

            @Schema(
                    description = "게시글 ID",
                    example = "1"
            )
            Long postId,

            @Schema(
                    description = "현재 회원의 좋아요 여부",
                    example = "true"
            )
            boolean liked,

            @Schema(
                    description = "좋아요 처리 결과 메시지",
                    example = "좋아요를 등록했습니다."
            )
            String message
    ) {
    }

    // =====================================================
    // 5. 좋아요 정보 조회 응답 DTO
    // =====================================================

    @Schema(
            name = "LikeInfoResponse",
            description = "게시글의 전체 좋아요 개수 및 현재 회원의 좋아요 상태"
    )
    public record LikeInfoResponse(

            @Schema(
                    description = "게시글 ID",
                    example = "1"
            )
            Long postId,

            @Schema(
                    description = "게시글 전체 좋아요 개수",
                    example = "5"
            )
            long likeCount,

            @Schema(
                    description = "현재 로그인한 회원의 좋아요 여부",
                    example = "true"
            )
            boolean liked
    ) {
    }
}
