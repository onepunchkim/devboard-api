package com.example.devboard.controller;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.service.PostLikeService;
import com.example.devboard.service.PostService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;

    public PostController(PostService postService, PostLikeService postLikeService) {
        this.postService = postService;
        this.postLikeService = postLikeService;
    }

    //게시글 생성
    @PostMapping
    public PostResponse createPost(
            @Valid  @RequestBody PostRequest request,
            Authentication authentication
    ) {

        System.out.println("인증 사용자 이름: " + authentication.getName());
        System.out.println("인증 사용자 권한: " + authentication.getAuthorities());

        return postService.createPost(
                request.title,
                request.content,
                authentication.getName()
        );
    }

    // 전체 게시글 조회 - 검색, 페이징, 정렬
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

    //특정 게시글 조회
    @GetMapping("/{id}")
    public PostResponse findById(@PathVariable Long id) {
        return postService.findById(id);
    }

    //게시글 수정
    @PutMapping("/{id}")
    public PostResponse updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest request,
            Authentication authentication
    ) {
        return postService.updatePost(
                id,
                request.title(),
                request.content(),
                authentication.getName()
        );
    }

    //게시글 삭제
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        postService.deletePost(id, authentication.getName());
    }

    //게시글 생성 요청 데이터
    public record PostRequest(String title, String content) {
    }

    // 여러 게시글의 좋아요 수를 한 번에 조회
    @GetMapping("/counts")
    public Map<Long, Long> getLikeCounts(
            @RequestParam List<Long> postIds
    ) {
        // 전달받은 게시글 ID 목록으로 좋아요 수를 일괄 조회
        Map<Long, Long> likeCounts = postLikeService.getLikeCounts(postIds);

        // 요청한 게시글 중 좋아요 수가 없는 게시글은 0으로 반환
        return postIds.stream()
                .distinct()
                .collect(Collectors.toMap(
                        postId -> postId,
                        postId -> likeCounts.getOrDefault(postId, 0L)
                ));
    }
}
