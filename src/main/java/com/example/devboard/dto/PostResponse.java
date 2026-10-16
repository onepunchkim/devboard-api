package com.example.devboard.dto;

import com.example.devboard.entity.Member;
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
        Member member = post.getMember();

        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                member != null ? member.getId() : null,
                member != null ? member.getNickname() : null
        );
    }
}
