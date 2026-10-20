package com.example.devboard.repository;


import com.example.devboard.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    //특정 게시글의 댓글 조회
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    //특정 회원이 작성한 댓글 조회
    List<Comment> findByMemberId(Long memberId);
}
