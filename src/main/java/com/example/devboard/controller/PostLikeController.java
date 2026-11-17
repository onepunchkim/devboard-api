package com.example.devboard.controller;

import com.example.devboard.service.PostLikeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts/{postId}/like")

@Tag(
        name = "좋아요 API",
        description = "게시글 좋아요 등록, 취소 및 좋아요 정보 조회 API"
)
public class PostLikeController {

    private final PostLikeService postLikeService;

    // 좋아요 서비스 생성자 주입
    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    // =====================================================
    // 좋아요 등록
    // =====================================================

    @Operation(
            summary = "게시글 좋아요 등록",
            description = "로그인한 사용자가 특정 게시글에 좋아요를 등록합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "좋아요 등록 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 또는 회원을 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 좋아요를 등록한 경우(서비스 정책에 따라 다름)"
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> likePost(

            @Parameter(
                    description = "좋아요를 등록할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long postId,

            // Spring Security 인증 정보는 Swagger 입력란에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // JWT 인증 필터가 저장한 회원 이메일 조회
        String email = authentication.getName();

        // 로그인한 회원의 좋아요 등록 처리
        postLikeService.likePostByEmail(email, postId);

        // 좋아요 등록 결과 반환
        return Map.of(
                "postId", postId,
                "liked", true,
                "message", "좋아요를 등록했습니다."
        );
    }

    // =====================================================
    // 좋아요 취소
    // =====================================================

    @Operation(
            summary = "게시글 좋아요 취소",
            description = "로그인한 사용자가 등록한 게시글 좋아요를 취소합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "좋아요 취소 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 또는 좋아요 정보를 찾을 수 없음"
            )
    })
    @DeleteMapping
    public Map<String, Object> unlikePost(

            @Parameter(
                    description = "좋아요를 취소할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long postId,

            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // 현재 로그인한 회원의 이메일 조회
        String email = authentication.getName();

        // 해당 회원이 등록한 좋아요 취소
        postLikeService.unlikePostByEmail(email, postId);

        // 좋아요 취소 결과 반환
        return Map.of(
                "postId", postId,
                "liked", false,
                "message", "좋아요를 취소했습니다."
        );
    }

    // =====================================================
    // 좋아요 정보 조회
    // =====================================================

    @Operation(
            summary = "게시글 좋아요 정보 조회",
            description = "게시글의 전체 좋아요 수와 현재 로그인한 사용자의 좋아요 여부를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "좋아요 정보 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음"
            )
    })
    @GetMapping
    public Map<String, Object> getLikeInfo(

            @Parameter(
                    description = "좋아요 정보를 조회할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long postId,

            @Parameter(hidden = true)
            Authentication authentication
    ) {
        // 현재 로그인한 회원의 이메일 조회
        String email = authentication.getName();

        // 해당 게시글의 전체 좋아요 수 조회
        long likeCount = postLikeService.getLikeCount(postId);

        // 현재 회원이 좋아요를 등록했는지 확인
        boolean liked = postLikeService.hasLikedByEmail(
                email,
                postId
        );

        // 좋아요 수와 현재 회원의 좋아요 상태 반환
        return Map.of(
                "postId", postId,
                "likeCount", likeCount,
                "liked", liked
        );
    }
}
