package com.example.devboard.service;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostLike;
import com.example.devboard.exception.BusinessException;
import com.example.devboard.exception.ErrorCode;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostLikeRepository;
import com.example.devboard.repository.PostRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;

    // =====================================================
    // 생성자 주입
    // =====================================================

    public PostLikeService(
            PostLikeRepository postLikeRepository,
            MemberRepository memberRepository,
            PostRepository postRepository
    ) {
        this.postLikeRepository = postLikeRepository;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
    }

    // =====================================================
    // 1. 좋아요 등록 - 회원 ID 기반
    // =====================================================

    @Transactional
    public void likePost(Long memberId, Long postId) {

        // 좋아요를 누른 회원이 존재하는지 확인
        Member member = findMember(memberId);

        // 좋아요 대상 게시글이 존재하는지 확인
        Post post = findPost(postId);

        // 동일한 회원이 같은 게시글에 중복으로 좋아요를 누르지 못하도록 처리
        if (postLikeRepository.existsByMemberIdAndPostId(
                memberId,
                postId
        )) {
            throw new BusinessException(
                    ErrorCode.LIKE_ALREADY_EXISTS
            );
        }

        // 회원과 게시글을 연결한 좋아요 기록 생성
        PostLike postLike = new PostLike(member, post);

        // 좋아요 기록을 데이터베이스에 저장
        postLikeRepository.save(postLike);
    }

    // =====================================================
    // 2. 좋아요 취소 - 회원 ID 기반
    // =====================================================

    @Transactional
    public void unlikePost(Long memberId, Long postId) {

        // 회원 존재 여부 확인
        findMember(memberId);

        // 게시글 존재 여부 확인
        findPost(postId);

        // 해당 회원이 등록한 좋아요 기록 조회
        PostLike postLike = postLikeRepository
                .findByMemberIdAndPostId(memberId, postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.LIKE_NOT_FOUND
                ));

        // 조회한 좋아요 기록 삭제
        postLikeRepository.delete(postLike);
    }

    // =====================================================
    // 3. 게시글 좋아요 수 조회
    // =====================================================

    public long getLikeCount(Long postId) {

        // 존재하지 않는 게시글의 좋아요 수 조회 방지
        findPost(postId);

        // 게시글에 등록된 좋아요 개수 반환
        return postLikeRepository.countByPostId(postId);
    }

    // =====================================================
    // 4. 여러 게시글의 좋아요 수 일괄 조회
    // =====================================================

    public Map<Long, Long> getLikeCounts(List<Long> postIds) {

        // 조회할 게시글 ID가 없으면 빈 결과 반환
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        // 게시글 ID별 좋아요 수를 저장할 Map
        Map<Long, Long> likeCounts = new HashMap<>();

        // 여러 게시글의 좋아요 수를 한 번의 쿼리로 조회
        List<Object[]> results =
                postLikeRepository.countLikesByPostIds(postIds);

        // 조회 결과를 Map으로 변환
        for (Object[] result : results) {

            Long postId = ((Number) result[0]).longValue();
            Long count = ((Number) result[1]).longValue();

            likeCounts.put(postId, count);
        }

        // 좋아요가 없는 게시글은 호출 측에서 0으로 처리
        return likeCounts;
    }

    // =====================================================
    // 5. 좋아요 여부 조회 - 회원 ID 기반
    // =====================================================

    public boolean hasLiked(Long memberId, Long postId) {

        // 게시글이 존재하는지 확인
        findPost(postId);

        // 회원과 게시글 조합의 좋아요 기록 존재 여부 반환
        return postLikeRepository.existsByMemberIdAndPostId(
                memberId,
                postId
        );
    }

    // =====================================================
    // 6. 좋아요 등록 - 이메일 기반
    // =====================================================

    @Transactional
    public void likePostByEmail(String email, Long postId) {

        // JWT에서 추출한 이메일로 회원 조회
        Member member = findMemberByEmail(email);

        // 회원 ID 기반 좋아요 등록 로직 재사용
        likePost(member.getId(), postId);
    }

    // =====================================================
    // 7. 좋아요 취소 - 이메일 기반
    // =====================================================

    @Transactional
    public void unlikePostByEmail(String email, Long postId) {

        // JWT에서 추출한 이메일로 회원 조회
        Member member = findMemberByEmail(email);

        // 회원 ID 기반 좋아요 취소 로직 재사용
        unlikePost(member.getId(), postId);
    }

    // =====================================================
    // 8. 좋아요 여부 조회 - 이메일 기반
    // =====================================================

    public boolean hasLikedByEmail(String email, Long postId) {

        // JWT에서 추출한 이메일로 회원 조회
        Member member = findMemberByEmail(email);

        // 회원 ID 기반 좋아요 여부 조회 로직 재사용
        return hasLiked(member.getId(), postId);
    }

    // =====================================================
    // 공통 메서드: 회원 ID로 조회
    // =====================================================

    private Member findMember(Long memberId) {

        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    // =====================================================
    // 공통 메서드: 이메일로 회원 조회
    // =====================================================

    private Member findMemberByEmail(String email) {

        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MEMBER_NOT_FOUND
                ));
    }

    // =====================================================
    // 공통 메서드: 게시글 조회
    // =====================================================

    private Post findPost(Long postId) {

        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.POST_NOT_FOUND
                ));
    }
}
