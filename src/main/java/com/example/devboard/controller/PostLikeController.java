package com.example.devboard.controller;

import com.example.devboard.service.PostLikeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts/{postId}/like")
public class PostLikeController {

    private final PostLikeService postLikeService;

    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    //좋아요 등록
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> likePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        //JWT 인증 필터가 인증정보에 저장한 회원 이메일을 가져옴
        String email = authentication.getName();

        //이메일로 회원 ID를 확인하기 위해 서비스에 전달
        postLikeService.likePostByEmail(email, postId);

        return Map.of(
                "postId", postId,
                "like", true,
                "message", "좋아요를 등록했습니다."
        );
    }

    //좋아요 취소
    @DeleteMapping
    public Map<String, Object> unlikePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        //현재 로그인한 회원의 이메일 조회
        String email = authentication.getName();

        //해당 회원이 등록한 좋아요 취소
        postLikeService.unlikePostByEmail(email, postId);

        return Map.of(
                "postId", postId,
                "liked", false,
                "message", "좋아요를 취소했습니다."
        );
    }

    //좋아요 수 조회
    @GetMapping
    public Map<String, Object> getLikeInfo(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        //현재 로그인한 회원의 이메일 조회
        String email = authentication.getName();

        //좋아요 수와 현재 회원의 좋아요 여부 조회
        long likeCount = postLikeService.getLIkeCount(postId);
        boolean liked = postLikeService.hasLikedByEmail(email, postId);

        return Map.of(
                "postId", postId,
                "likeCount", likeCount,
                "liked", liked
        );
    }
}
