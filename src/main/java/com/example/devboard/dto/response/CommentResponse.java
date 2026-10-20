package com.example.devboard.dto.response;

import com.example.devboard.entity.Comment;

import java.time.LocalDateTime;

public record CommentResponse(

        Long id,
        String content,
        Long postId,
        Long memberId,
        String nickname,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getPost().getId(),
                comment.getMember().getId(),
                comment.getMember().getNickname(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
