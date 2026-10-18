package com.example.devboard.controller;

import com.example.devboard.dto.PostResponse;
import com.example.devboard.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
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

    //전체 게시글 조회
    @GetMapping
    public List<PostResponse> findAll() {
        return postService.findAll();
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
}
