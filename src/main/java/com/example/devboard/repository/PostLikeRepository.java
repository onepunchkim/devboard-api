package com.example.devboard.repository;

import com.example.devboard.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// 좋아요 데이터의 저장, 조회, 삭제를 담당하는 Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    // 특정 회원이 특정 게시글에 등록한 좋아요 기록 조회
    Optional<PostLike> findByMemberIdAndPostId(Long memberId, Long postId);

    // 특정 게시글의 전체 좋아요 수 조회
    long countByPostId(Long postId);

    // 특정 회원이 특정 게시글에 좋아요를 눌렀는지 확인
    // 중요: existBy가 아니라 existsBy로 작성해야 함
    boolean existsByMemberIdAndPostId(Long memberId, Long postId);

    // 게시글 삭제 시 해당 게시글의 좋아요 기록 삭제
    void deleteByPostId(Long postId);

    // 회원 탈퇴 시 해당 회원의 좋아요 기록 삭제
    void deleteByMemberId(Long memberId);

    // 여러 게시글의 좋아요 수를 한 번에 조회
    @Query("""
    SELECT pl.post.id, COUNT(pl.id)
    FROM PostLike pl
    WHERE pl.post.id IN :postIds
    GROUP BY pl.post.id
    """)
    List<Object[]> countLikesByPostIds(@Param("postIds") List<Long> postIds);
}
