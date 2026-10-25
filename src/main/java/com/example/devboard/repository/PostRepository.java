package com.example.devboard.repository;

import com.example.devboard.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 특정 회원의 게시글을 DB에서 바로 삭제
    @Modifying
    @Query("DELETE FROM Post p WHERE p.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    //제목에 검색어가 포함된 게시글을 페이징 조회
    Page<Post> findByTitleContaining(
            String keyword,
            Pageable pageable
    );

    // 제목 또는 내용에 검색어가 포함된 게시글 검색
    Page<Post> findByTitleContainingOrContentContaining(
            String titleKeyword,
            String contentKeyword,
            Pageable pageable
    );
}
