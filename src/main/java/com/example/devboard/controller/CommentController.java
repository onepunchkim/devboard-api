package com.example.devboard.controller;

import com.example.devboard.dto.request.CommentCreateRequest;
import com.example.devboard.dto.response.CommentResponse;
import com.example.devboard.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    //댓글 작성
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            Authentication authentication
    ) {
        return commentService.createComment(postId, authentication.getName(), request);
    }

    //특정 게시글의 댓글 목록 조회
    @GetMapping("/posts/{postId}/comments")
    public List<CommentResponse> findComments(@PathVariable Long postId) {
        return commentService.findComments(postId);
    }

    //댓글 수정
    @PutMapping("/comments/{commentId}")
    public CommentResponse updateComment(
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody CommentCreateRequest request,
            Authentication authentication
    ) {
        return commentService.updateComment(commentId, authentication.getName(), request);
    }

    //댓글 삭제
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId, Authentication authentication) {
        commentService.deleteComment(commentId, authentication.getName());
    }
}
