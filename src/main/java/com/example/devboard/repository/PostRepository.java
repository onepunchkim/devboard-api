package com.example.devboard.repository;

import com.example.devboard.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 전체 게시글 조회 시 작성자 정보도 함께 조회
    // LAZY 로딩으로 발생하는 N+1 문제 방지
    @Override
    @EntityGraph(attributePaths = "member")
    Page<Post> findAll(Pageable pageable);

    // 특정 회원의 게시글을 DB에서 바로 삭제
    @Modifying
    @Query("DELETE FROM Post p WHERE p.member.id = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    // 제목 검색 시 작성자 정보도 함께 조회
    @EntityGraph(attributePaths = "member")
    Page<Post> findByTitleContaining(
            String keyword,
            Pageable pageable
    );

    // 제목 또는 내용 검색 시 작성자 정보도 함께 조회
    @EntityGraph(attributePaths = "member")
    Page<Post> findByTitleContainingOrContentContaining(
            String titleKeyword,
            String contentKeyword,
            Pageable pageable
    );
}