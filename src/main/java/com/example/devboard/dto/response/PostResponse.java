package com.example.devboard.dto.response;

import com.example.devboard.entity.Post;

import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long memberId,
        String nickname
) {
    public static PostResponse from(Post post) {
        Long memberId = null;
        String nickname = null;

        if (post.getMember() != null) {
            memberId = post.getMember().getId();
            nickname = post.getMember().getNickname();
        }

        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                memberId,
                nickname
        );
    }
}
