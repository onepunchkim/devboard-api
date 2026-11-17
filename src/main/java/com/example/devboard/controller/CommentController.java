package com.example.devboard.controller;

import com.example.devboard.dto.request.CommentCreateRequest;
import com.example.devboard.dto.response.CommentResponse;
import com.example.devboard.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")

@Tag(
        name = "댓글 API",
        description = "댓글 작성, 목록 조회, 수정 및 삭제 API"
)
public class CommentController {

    private final CommentService commentService;

    // 댓글 서비스 생성자 주입
    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // =====================================================
    // 댓글 작성
    // =====================================================

    @Operation(
            summary = "댓글 작성",
            description = "로그인한 사용자가 특정 게시글에 댓글을 작성합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "댓글 작성 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "댓글 입력값 검증 실패"
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
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(

            @Parameter(
                    description = "댓글을 작성할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long postId,

            // 댓글 작성 요청 DTO
            @Valid @RequestBody CommentCreateRequest request,

            // Spring Security 인증 정보는 Swagger에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 로그인한 사용자 이메일을 전달하여 댓글 생성
        return commentService.createComment(
                postId,
                authentication.getName(),
                request
        );
    }

    // =====================================================
    // 특정 게시글의 댓글 목록 조회
    // =====================================================

    @Operation(
            summary = "댓글 목록 조회",
            description = "특정 게시글에 등록된 댓글 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "댓글 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음"
            )
    })
    @GetMapping("/posts/{postId}/comments")
    public List<CommentResponse> findComments(

            @Parameter(
                    description = "댓글을 조회할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long postId
    ) {

        // 게시글 ID에 해당하는 댓글 목록 조회
        return commentService.findComments(postId);
    }

    // =====================================================
    // 댓글 수정
    // =====================================================

    @Operation(
            summary = "댓글 수정",
            description = "댓글 작성자만 기존 댓글 내용을 수정할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "댓글 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "댓글 입력값 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "댓글 작성자 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "댓글을 찾을 수 없음"
            )
    })
    @PutMapping("/comments/{commentId}")
    public CommentResponse updateComment(

            @Parameter(
                    description = "수정할 댓글 ID",
                    example = "1"
            )
            @PathVariable("commentId") Long commentId,

            // 수정할 댓글 내용
            @Valid @RequestBody CommentCreateRequest request,

            // 인증 정보는 Swagger 입력 항목에서 제외
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 댓글 ID, 사용자 이메일, 수정 내용을 서비스로 전달
        return commentService.updateComment(
                commentId,
                authentication.getName(),
                request
        );
    }

    // =====================================================
    // 댓글 삭제
    // =====================================================

    @Operation(
            summary = "댓글 삭제",
            description = "댓글 작성자만 자신의 댓글을 삭제할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "댓글 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "댓글 작성자 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "댓글을 찾을 수 없음"
            )
    })
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(

            @Parameter(
                    description = "삭제할 댓글 ID",
                    example = "1"
            )
            @PathVariable Long commentId,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 로그인한 사용자 이메일을 이용해 작성자 권한 확인 후 삭제
        commentService.deleteComment(
                commentId,
                authentication.getName()
        );
    }
}
