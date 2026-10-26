package com.example.devboard.service;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostLike;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostLikeRepository;
import com.example.devboard.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;

    public PostLikeService(PostLikeRepository postLikeRepository, MemberRepository memberRepository, PostRepository postRepository) {
        this.postLikeRepository = postLikeRepository;
        this.memberRepository = memberRepository;
        this.postRepository = postRepository;
    }

    //특정 회원이 게시글에 좋아요를 등록
    @Transactional
    public void likePost(Long memberId, Long postId) {

        //좋아요를 누른 회원이 실제로 존재하는지 확인
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        //좋아요 대상 게시글이 실제로 존재하는지 확인
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        //이미 좋아요를 눌렀다면 중복X
        if (postLikeRepository.existsByMemberIdAndPostId(memberId, postId)) {
            throw new IllegalStateException("이미 좋아요를 누른 게시글 입니다.");
        }

        //회원과 게시글을 연결한 좋아요 기록 생성
        PostLike postLike = new PostLike(member, post);

        //좋아요 기록을 DB 저장
        postLikeRepository.save(postLike);
    }

    // 특정 회원의 게시글 좋아요를 취소
    @Transactional
    public void unlikePost(Long memberId, Long postId) {

        // 회원이 실제로 존재하는지 확인
        if (!memberRepository.existsById(memberId)) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        }

        // 게시글이 실제로 존재하는지 확인
        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }

        // 해당 회원이 해당 게시글에 등록한 좋아요 기록 조회
        PostLike postLike = postLikeRepository
                .findByMemberIdAndPostId(memberId, postId)
                .orElseThrow(() ->
                        new IllegalArgumentException("좋아요 기록이 없습니다.")
                );

        // 조회한 좋아요 기록 삭제
        postLikeRepository.delete(postLike);
    }

    //게시글에 등록된 전체 좋아요 수 조회
    @Transactional(readOnly = true)
    public long getLikeCount(Long postId) {

        //존재하지 않는 게시글에 대한 조회 방지
        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }

        //해당 게시글의 좋아요 개수 반환
        return postLikeRepository.countByPostId(postId);
    }

    // 여러 게시글의 좋아요 수를 한 번에 조회
    @Transactional(readOnly = true)
    public Map<Long, Long> getLikeCounts(List<Long> postIds) {

        // 조회할 게시글 ID가 없으면 빈 결과 반환
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        // 게시글 ID별 좋아요 수를 저장할 Map 생성
        Map<Long, Long> likeCounts = new HashMap<>();

        // DB에서 게시글별 좋아요 수를 일괄 조회
        List<Object[]> results =
                postLikeRepository.countLikesByPostIds(postIds);

        // 조회 결과를 게시글 ID와 좋아요 수의 Map으로 변환
        for (Object[] result : results) {
            Long postId = ((Number) result[0]).longValue();
            Long count = ((Number) result[1]).longValue();

            likeCounts.put(postId, count);
        }

        // 좋아요가 없는 게시글은 프론트엔드에서 0으로 처리할 수 있다.
        return likeCounts;
    }

    //특정 회원이 해당 게시글에 좋아요를 눌렀는지 조회
    @Transactional(readOnly = true)
    public boolean hasLiked(Long memberId, Long postId) {

        //게시글이 존재하는지 확인
        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }

        //DB에 회원과 게시글 조합의 좋아요 기록이 있는지 확인
        return postLikeRepository.existsByMemberIdAndPostId(memberId, postId);
    }

    // 이메일로 회원을 찾아 좋아요를 등록
    @Transactional
    public void likePostByEmail(String email, Long postId) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        // 기존 회원 ID 기반 메서드의 검증 및 저장 로직 재사용
        likePost(member.getId(), postId);
    }

    // 이메일로 회원을 찾아 좋아요를 취소
    @Transactional
    public void unlikePostByEmail(String email, Long postId) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        unlikePost(member.getId(), postId);
    }

    // 이메일로 현재 회원의 좋아요 여부 확인
    @Transactional(readOnly = true)
    public boolean hasLikedByEmail(String email, Long postId) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("회원을 찾을 수 없습니다.")
                );

        return hasLiked(member.getId(), postId);
    }
}
