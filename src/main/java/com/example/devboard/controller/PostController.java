package com.example.devboard.controller;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.PostImage;
import com.example.devboard.service.PostImageService;
import com.example.devboard.service.PostLikeService;
import com.example.devboard.service.PostService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/posts")
@Tag(
        name = "게시글 API",
        description = "게시글 CRUD, 검색, 이미지 관리 및 좋아요 수 조회 API"
)
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;
    private final PostImageService postImageService;

    // =====================================================
    // 생성자 주입
    // =====================================================

    public PostController(
            PostService postService,
            PostLikeService postLikeService,
            PostImageService postImageService
    ) {
        this.postService = postService;
        this.postLikeService = postLikeService;
        this.postImageService = postImageService;
    }

    // =====================================================
    // 1. 게시글 작성
    // =====================================================

    @Operation(
            summary = "게시글 작성",
            description = "JWT 인증된 사용자가 새로운 게시글을 작성합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 작성 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "제목 또는 내용 입력값 검증 실패 (INVALID_INPUT)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "작성 회원을 찾을 수 없음 (MEMBER_NOT_FOUND)"
            )
    })
    @PostMapping
    public PostResponse createPost(
            @Valid @RequestBody PostRequest request,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 게시글 제목, 내용, 로그인한 사용자 이메일 전달
        return postService.createPost(
                request.title(),
                request.content(),
                authentication.getName()
        );
    }

    // =====================================================
    // 2. 게시글 이미지 업로드
    // =====================================================

    @Operation(
            summary = "게시글 이미지 업로드",
            description = "게시글 작성자가 이미지 파일을 업로드합니다. " +
                    "게시글당 최대 5장까지 등록할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이미지 업로드 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "업로드 파일 없음, 빈 파일 포함, " +
                            "이미지 5장 제한 초과 (HTTP_400) 또는 " +
                            "게시글 ID 형식 오류 (TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "게시글 작성자 권한 없음 (HTTP_403)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (HTTP_404)"
            )
    })
    @PostMapping(
            value = "/{id}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public List<PostImageResponse> uploadImages(

            @Parameter(
                    description = "이미지를 등록할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long id,

            @Parameter(
                    description = "업로드할 이미지 파일 목록"
            )
            @RequestParam("files") List<MultipartFile> files,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 게시글 존재 여부, 작성자 권한, 이미지 개수 확인 후 업로드
        List<PostImage> images = postImageService.uploadImages(
                id,
                files,
                authentication.getName()
        );

        // 저장된 이미지 정보를 응답 DTO로 변환
        return images.stream()
                .map(PostImageResponse::from)
                .toList();
    }

    // =====================================================
    // 3. 게시글 목록 조회 및 검색
    // =====================================================

    @Operation(
            summary = "게시글 목록 조회",
            description = "검색어, 페이지 번호, 페이지 크기, " +
                    "정렬 조건을 이용해 게시글 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 페이지 번호 또는 크기 (INVALID_PAGE), " +
                            "지원하지 않는 정렬 조건 (INVALID_SORT), " +
                            "파라미터 형식 오류 (TYPE_MISMATCH)"
            )
    })
    @GetMapping
    public Page<PostResponse> findAll(

            @Parameter(
                    description = "제목 또는 내용 검색어",
                    example = "Spring"
            )
            @RequestParam(required = false)
            String keyword,

            @Parameter(
                    description = "페이지 번호 (0부터 시작)",
                    example = "0"
            )
            @RequestParam(defaultValue = "0")
            int page,

            @Parameter(
                    description = "페이지 크기 (1~100)",
                    example = "10"
            )
            @RequestParam(defaultValue = "10")
            int size,

            @Parameter(
                    description = "정렬 조건: latest 또는 oldest",
                    example = "latest",
                    schema = @Schema(
                            allowableValues = {"latest", "oldest"}
                    )
            )
            @RequestParam(defaultValue = "latest")
            String sort
    ) {

        // 검색 조건을 서비스 계층에 전달
        return postService.findAll(
                keyword,
                page,
                size,
                sort
        );
    }

    // =====================================================
    // 4. 게시글 상세 조회
    // =====================================================

    @Operation(
            summary = "게시글 상세 조회",
            description = "게시글 ID를 이용해 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 상세 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류 (TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (POST_NOT_FOUND)"
            )
    })
    @GetMapping("/{id}")
    public PostResponse findById(

            @Parameter(
                    description = "조회할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long id
    ) {

        // 게시글이 존재하지 않으면 POST_NOT_FOUND 예외 발생
        return postService.findById(id);
    }

    // =====================================================
    // 5. 게시글 수정
    // =====================================================

    @Operation(
            summary = "게시글 수정",
            description = "게시글 작성자만 제목과 내용을 수정할 수 있습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "게시글 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 (INVALID_INPUT) 또는 " +
                            "게시글 ID 형식 오류 (TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "작성자 권한 없음 (POST_ACCESS_DENIED)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (POST_NOT_FOUND)"
            )
    })
    @PutMapping("/{id}")
    public PostResponse updatePost(

            @Parameter(
                    description = "수정할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long id,

            @Valid @RequestBody PostRequest request,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 작성자 권한 확인 후 게시글 제목과 내용 수정
        return postService.updatePost(
                id,
                request.title(),
                request.content(),
                authentication.getName()
        );
    }

    // =====================================================
    // 6. 게시글 삭제
    // =====================================================

    @Operation(
            summary = "게시글 삭제",
            description = "게시글 작성자만 게시글을 삭제할 수 있습니다. " +
                    "삭제 시 댓글, 좋아요 기록 및 이미지도 정리합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "게시글 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류 (TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "작성자 권한 없음 (POST_ACCESS_DENIED)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (POST_NOT_FOUND)"
            )
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(

            @Parameter(
                    description = "삭제할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long id,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 작성자 권한 확인 후 게시글 및 관련 데이터 삭제
        postService.deletePost(
                id,
                authentication.getName()
        );
    }

    // =====================================================
    // 7. 게시글 이미지 목록 조회
    // =====================================================

    @Operation(
            summary = "게시글 이미지 목록 조회",
            description = "특정 게시글에 등록된 이미지 목록을 " +
                    "이미지 ID 오름차순으로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "이미지 목록 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 형식 오류 (TYPE_MISMATCH)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글을 찾을 수 없음 (HTTP_404)"
            )
    })
    @GetMapping("/{id}/images")
    public List<PostImageResponse> getPostImages(

            @Parameter(
                    description = "이미지를 조회할 게시글 ID",
                    example = "1"
            )
            @PathVariable Long id
    ) {

        // 게시글 존재 여부 확인 후 이미지 목록 조회
        return postImageService.findImagesByPostId(id)
                .stream()
                .map(PostImageResponse::from)
                .toList();
    }

    // =====================================================
    // 8. 게시글 이미지 삭제
    // =====================================================

    @Operation(
            summary = "게시글 이미지 삭제",
            description = "게시글 작성자가 특정 이미지를 삭제합니다. " +
                    "다른 게시글에 속한 이미지는 삭제할 수 없습니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "이미지 삭제 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 또는 이미지 ID 형식 오류 (TYPE_MISMATCH), " +
                            "다른 게시글에 속한 이미지 삭제 요청 (HTTP_400)"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 인증 필요"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "게시글 작성자 권한 없음 (HTTP_403)"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "게시글 또는 이미지를 찾을 수 없음 (HTTP_404)"
            )
    })
    @DeleteMapping("/{postId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePostImage(

            @Parameter(
                    description = "게시글 ID",
                    example = "1"
            )
            @PathVariable Long postId,

            @Parameter(
                    description = "삭제할 이미지 ID",
                    example = "5"
            )
            @PathVariable Long imageId,

            @Parameter(hidden = true)
            Authentication authentication
    ) {

        // 게시글 작성자 및 이미지 소속 여부 확인 후 삭제
        postImageService.deleteImage(
                postId,
                imageId,
                authentication.getName()
        );
    }

    // =====================================================
    // 9. 여러 게시글의 좋아요 수 일괄 조회
    // =====================================================

    @Operation(
            summary = "게시글 좋아요 수 일괄 조회",
            description = "여러 게시글 ID를 전달하여 좋아요 수를 한 번에 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "좋아요 수 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "게시글 ID 목록의 형식 오류 (TYPE_MISMATCH)"
            )
    })
    @GetMapping("/counts")
    public Map<Long, Long> getLikeCounts(

            @Parameter(
                    description = "조회할 게시글 ID 목록 (쉼표로 구분)",
                    example = "1,2,3"
            )
            @RequestParam List<Long> postIds
    ) {

        // 게시글 ID 목록에 대한 좋아요 수 일괄 조회
        Map<Long, Long> likeCounts =
                postLikeService.getLikeCounts(postIds);

        // 조회되지 않은 게시글은 좋아요 수 0으로 처리
        return postIds.stream()
                .distinct()
                .collect(Collectors.toMap(
                        postId -> postId,
                        postId -> likeCounts.getOrDefault(postId, 0L)
                ));
    }

    // =====================================================
    // 게시글 작성 및 수정 요청 DTO
    // =====================================================

    @Schema(
            description = "게시글 작성 및 수정 요청"
    )
    public record PostRequest(

            @Schema(
                    description = "게시글 제목",
                    example = "Spring Boot 게시판 만들기",
                    maxLength = 200,
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @NotBlank(message = "제목은 필수입니다.")
            @Size(
                    max = 200,
                    message = "제목은 200자 이하여야 합니다."
            )
            String title,

            @Schema(
                    description = "게시글 내용",
                    example = "Spring Boot와 JPA를 이용한 게시판 프로젝트입니다.",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @NotBlank(message = "내용은 필수입니다.")
            String content
    ) {
    }

    // =====================================================
    // 게시글 이미지 응답 DTO
    // =====================================================

    @Schema(
            description = "게시글 이미지 정보"
    )
    public record PostImageResponse(

            @Schema(
                    description = "이미지 ID",
                    example = "1"
            )
            Long id,

            @Schema(
                    description = "이미지 저장 경로",
                    example = "/uploads/posts/example.jpg"
            )
            String imagePath,

            @Schema(
                    description = "원본 파일명",
                    example = "photo.jpg"
            )
            String originalFilename
    ) {

        // 이미지 엔티티를 응답 DTO로 변환
        public static PostImageResponse from(PostImage image) {

            return new PostImageResponse(
                    image.getId(),
                    image.getImagePath(),
                    image.getOriginalFilename()
            );
        }
    }
}
