package com.example.devboard.controller;

import com.example.devboard.dto.request.CommentCreateRequest;
import com.example.devboard.dto.response.CommentResponse;
import com.example.devboard.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    // =====================================================
    // 생성자 주입
    // =====================================================

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    // =====================================================
    // 1. 댓글 작성 API
    // =====================================================

    @Operation(
            summary = "댓글 작성",
            description = """
                    로그인한 사용자가 특정 게시글에 댓글을 작성합니다.

                    - JWT 인증이 필요합니다.
                    - 댓글 내용은 Bean Validation으로 검증합니다.
                    - 존재하지 않는 게시글에는 댓글을 작성할 수 없습니다.
                    - 댓글 작성 성공 시 생성된 댓글 정보를 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "댓글 작성 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = CommentResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "댓글 입력값 또는 게시글 ID 형식 오류"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 또는 회원을 찾을 수 없음"
            )
    })
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(

            @Parameter(
                    description = "댓글을 작성할 게시글 ID",
                    example = "1",
                    required = true
            )
            @PathVariable Long postId,

            // 댓글 작성 요청 DTO
            @Valid @RequestBody CommentCreateRequest request,

            // 인증 객체는 Swagger 입력란에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // JWT에서 로그인한 사용자 이메일 조회
        String email = authentication.getName();

        // 댓글 작성 서비스 호출
        return commentService.createComment(
                postId,
                email,
                request
        );
    }

    // =====================================================
    // 2. 댓글 목록 조회 API
    // =====================================================

    @Operation(
            summary = "댓글 목록 조회",
            description = """
                    특정 게시글에 작성된 댓글 목록을 조회합니다.

                    - 댓글이 없으면 빈 목록을 반환합니다.
                    - 조회 결과는 CommentResponse 배열입니다.
                    - 게시글 존재 여부는 서비스에서 검증합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "댓글 목록 조회 성공",
                    content = @Content(
                            array = @ArraySchema(
                                    schema = @Schema(
                                            implementation = CommentResponse.class
                                    )
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류"
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
                    example = "1",
                    required = true
            )
            @PathVariable Long postId
    ) {

        // 게시글에 작성된 댓글 목록 조회
        return commentService.findComments(postId);
    }

    // =====================================================
    // 3. 댓글 수정 API
    // =====================================================

    @Operation(
            summary = "댓글 수정",
            description = """
                    로그인한 사용자가 자신이 작성한 댓글을 수정합니다.

                    - JWT 인증이 필요합니다.
                    - 댓글 작성자만 수정할 수 있습니다.
                    - 댓글 내용은 Bean Validation으로 검증합니다.
                    - 수정 성공 시 변경된 댓글 정보를 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "댓글 수정 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = CommentResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "댓글 입력값 또는 댓글 ID 형식 오류"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "댓글 작성자가 아니므로 수정 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "댓글 또는 회원을 찾을 수 없음"
            )
    })
    @PutMapping("/comments/{commentId}")
    public CommentResponse updateComment(

            @Parameter(
                    description = "수정할 댓글 ID",
                    example = "1",
                    required = true
            )
            @PathVariable("commentId") Long commentId,

            // 수정할 댓글 내용
            @Valid @RequestBody CommentCreateRequest request,

            // 인증 객체는 Swagger 입력란에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // JWT에서 로그인한 사용자 이메일 조회
        String email = authentication.getName();

        // 댓글 작성자 권한 확인 후 수정
        return commentService.updateComment(
                commentId,
                email,
                request
        );
    }

    // =====================================================
    // 4. 댓글 삭제 API
    // =====================================================

    @Operation(
            summary = "댓글 삭제",
            description = """
                    로그인한 사용자가 자신이 작성한 댓글을 삭제합니다.

                    - JWT 인증이 필요합니다.
                    - 댓글 작성자만 삭제할 수 있습니다.
                    - 삭제 성공 시 204 No Content를 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "댓글 삭제 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "댓글 ID 형식 오류"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "댓글 작성자가 아니므로 삭제 권한 없음"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "댓글 또는 회원을 찾을 수 없음"
            )
    })
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(

            @Parameter(
                    description = "삭제할 댓글 ID",
                    example = "1",
                    required = true
            )
            @PathVariable Long commentId,

            // 인증 객체는 Swagger 입력란에서 숨김
            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // JWT에서 로그인한 사용자 이메일 조회
        String email = authentication.getName();

        // 댓글 작성자 권한 확인 후 삭제
        commentService.deleteComment(
                commentId,
                email
        );
    }
}
