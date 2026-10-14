package com.example.devboard.controller;

import com.example.devboard.entity.Post;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.service.PostService;
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
    public Post createPost(@RequestBody PostRequest request) {
        return postService.createPost(
                request.title(),
                request.content()
        );
    }

    //전체 게시글 조회
    @GetMapping
    public List<Post> getPosts() {
        return postService.getPosts();
    }

    //특정 게시글 조회
    @GetMapping("/{id}")
    public Post getPost(@PathVariable Long id) {
        return postService.getPost(id);
    }

    //게시글 수정
    @PutMapping("/{id}")
    public Post updatePost(
            @PathVariable Long id,
            @RequestBody PostRequest request
    ) {
        return postService.updatePost(
                id,
                request.title(),
                request.content()
        );
    }

    //게시글 삭제
    @DeleteMapping("/{id}")
    public void deletePost(@PathVariable Long id) {
        postService.deletePost(id);
    }

    //게시글 생성 요청 데이터
    public record PostRequest(String title, String content) {
    }
}
