package com.example.devboard.controller;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.PostImage;
import com.example.devboard.service.PostImageService;
import com.example.devboard.service.PostLikeService;
import com.example.devboard.service.PostService;
import jakarta.validation.Valid;
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
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;
    private final PostImageService postImageService;

    // 필요한 서비스들을 생성자로 주입
    public PostController(
            PostService postService,
            PostLikeService postLikeService,
            PostImageService postImageService
    ) {
        this.postService = postService;
        this.postLikeService = postLikeService;
        this.postImageService = postImageService;
    }

    // 게시글 생성
    @PostMapping
    public PostResponse createPost(
            @Valid @RequestBody PostRequest request,
            Authentication authentication
    ) {
        // 인증된 사용자 정보 확인
        System.out.println("인증 사용자 이름: " + authentication.getName());
        System.out.println("인증 사용자 권한: " + authentication.getAuthorities());

        // 제목, 내용, 작성자 이메일을 전달해 게시글 생성
        return postService.createPost(
                request.title(),
                request.content(),
                authentication.getName()
        );
    }

    // 게시글 이미지 업로드
    @PostMapping(
            value = "/{id}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public List<PostImageResponse> uploadImages(
            @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication
    ) {
        // 로그인한 사용자의 이메일을 전달해 게시글 작성자 권한 확인
        List<PostImage> images = postImageService.uploadImages(
                id,
                files,
                authentication.getName()
        );

        // 이미지 엔티티를 응답 DTO로 변환해 반환
        return images.stream()
                .map(PostImageResponse::from)
                .toList();
    }

    // 전체 게시글 조회: 검색, 페이징, 정렬
    @GetMapping
    public Page<PostResponse> findAll(
            // 제목 또는 내용 검색어
            @RequestParam(required = false) String keyword,

            // 페이지 번호: 기본값 0
            @RequestParam(defaultValue = "0") int page,

            // 페이지 크기: 기본값 10
            @RequestParam(defaultValue = "10") int size,

            // 정렬 기준: latest(최신순), oldest(오래된순)
            @RequestParam(defaultValue = "latest") String sort
    ) {
        // 검색어, 페이지, 페이지 크기, 정렬 기준을 서비스로 전달
        return postService.findAll(keyword, page, size, sort);
    }

    // 특정 게시글 조회
    @GetMapping("/{id}")
    public PostResponse findById(@PathVariable Long id) {
        return postService.findById(id);
    }

    // 게시글 수정
    @PutMapping("/{id}")
    public PostResponse updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest request,
            Authentication authentication
    ) {
        // 게시글 ID, 수정할 제목과 내용, 사용자 이메일을 전달
        return postService.updatePost(
                id,
                request.title(),
                request.content(),
                authentication.getName()
        );
    }

    // 게시글 삭제
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        // 작성자 확인을 포함한 게시글 삭제 처리
        postService.deletePost(id, authentication.getName());
    }

    // 특정 게시글에 등록된 이미지 목록 조회
    @GetMapping("/{id}/images")
    public List<PostImageResponse> getPostImages(
            @PathVariable Long id
    ) {
        // 게시글 ID로 이미지 목록을 조회하고 응답 DTO로 변환
        return postImageService.findImagesByPostId(id)
                .stream()
                .map(PostImageResponse::from)
                .toList();
    }

    // 게시글 이미지 한 장 삭제
    @DeleteMapping("/{postId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePostImage(
            @PathVariable Long postId,
            @PathVariable Long imageId,
            Authentication authentication
    ) {
        // 로그인한 사용자의 이메일을 전달해 작성자 권한 확인
        postImageService.deleteImage(
                postId,
                imageId,
                authentication.getName()
        );
    }

    // 여러 게시글의 좋아요 수를 한 번에 조회
    @GetMapping("/counts")
    public Map<Long, Long> getLikeCounts(
            @RequestParam List<Long> postIds
    ) {
        // 전달받은 게시글 ID 목록으로 좋아요 수를 일괄 조회
        Map<Long, Long> likeCounts =
                postLikeService.getLikeCounts(postIds);

        // 좋아요 수가 조회되지 않은 게시글은 0으로 반환
        return postIds.stream()
                .distinct()
                .collect(Collectors.toMap(
                        postId -> postId,
                        postId -> likeCounts.getOrDefault(postId, 0L)
                ));
    }

    // 게시글 생성 요청 데이터
    public record PostRequest(
            String title,
            String content
    ) {
    }

    // 이미지 정보를 클라이언트에 전달하는 응답 DTO
    public record PostImageResponse(
            Long id,
            String imagePath,
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
