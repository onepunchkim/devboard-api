package com.example.devboard.controller;

import com.example.devboard.dto.PostResponse;
import com.example.devboard.entity.Post;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.service.PostService;
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
    public ResponseEntity<PostResponse> createPost(
            @RequestBody PostRequest request,
            Authentication authentication
    ) {
        PostResponse response = postService.createPost(
                request.title(),
                request.content(),
                authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //전체 게시글 조회
    @GetMapping
    public List<PostResponse> getPosts() {
        return postService.getPosts();
    }

    //특정 게시글 조회
    @GetMapping("/{id}")
    public PostResponse getPost(@PathVariable Long id) {
        return postService.getPost(id);
    }

    //게시글 수정
    @PutMapping("/{id}")
    public PostResponse updatePost(
            @PathVariable Long id,
            @RequestBody PostRequest request,
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
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication authentication
    ) {
        postService.deletePost(id, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    //게시글 생성 요청 데이터
    public record PostRequest(String title, String content) {
    }
}
