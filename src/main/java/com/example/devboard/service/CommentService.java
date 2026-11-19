package com.example.devboard.service;

import com.example.devboard.dto.request.CommentCreateRequest;
import com.example.devboard.dto.response.CommentResponse;

import com.example.devboard.entity.Comment;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;

import com.example.devboard.exception.BusinessException;
import com.example.devboard.exception.ErrorCode;

import com.example.devboard.repository.CommentRepository;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    // =====================================================
    // 생성자 주입
    // =====================================================

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            MemberRepository memberRepository
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.memberRepository = memberRepository;
    }

    // =====================================================
    // 1. 댓글 작성
    // =====================================================

    @Transactional
    public CommentResponse createComment(
            Long postId,
            String email,
            CommentCreateRequest request
    ) {

        // 댓글을 작성할 게시글 조회
        // 게시글이 존재하지 않으면 404 오류 발생
        Post post = postRepository.findById(postId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.POST_NOT_FOUND)
                );

        // 댓글을 작성할 회원 조회
        // 회원이 존재하지 않으면 404 오류 발생
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.MEMBER_NOT_FOUND)
                );

        // 댓글 내용의 앞뒤 공백 제거 후 엔티티 생성
        Comment comment = new Comment(
                request.content().trim(),
                post,
                member
        );

        // 댓글 저장
        Comment savedComment = commentRepository.save(comment);

        // 저장된 댓글 정보를 응답 DTO로 변환
        return CommentResponse.from(savedComment);
    }

    // =====================================================
    // 2. 특정 게시글의 댓글 목록 조회
    // =====================================================

    public List<CommentResponse> findComments(Long postId) {

        // 게시글 존재 여부 확인
        // 존재하지 않는 게시글이면 404 오류 발생
        if (!postRepository.existsById(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 해당 게시글의 댓글을 작성일 오름차순으로 조회
        return commentRepository
                .findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(CommentResponse::from)
                .toList();
    }

    // =====================================================
    // 3. 댓글 수정
    // =====================================================

    @Transactional
    public CommentResponse updateComment(
            Long commentId,
            String email,
            CommentCreateRequest request
    ) {

        // 수정할 댓글 조회
        // 댓글이 존재하지 않으면 404 오류 발생
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.COMMENT_NOT_FOUND)
                );

        // 댓글 작성자와 현재 로그인한 사용자가 같은지 확인
        checkCommentOwner(comment, email);

        // 댓글 내용 앞뒤 공백 제거 후 수정
        comment.updateContent(request.content().trim());

        // 수정된 댓글 정보를 응답 DTO로 반환
        return CommentResponse.from(comment);
    }

    // =====================================================
    // 4. 댓글 삭제
    // =====================================================

    @Transactional
    public void deleteComment(Long commentId, String email) {

        // 삭제할 댓글 조회
        // 댓글이 존재하지 않으면 404 오류 발생
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.COMMENT_NOT_FOUND)
                );

        // 댓글 작성자 권한 확인
        checkCommentOwner(comment, email);

        // 댓글 삭제
        commentRepository.delete(comment);
    }

    // =====================================================
    // 5. 댓글 작성자 권한 확인
    // =====================================================

    private void checkCommentOwner(Comment comment, String email) {

        // 댓글 작성자의 이메일과 로그인한 사용자 이메일 비교
        if (!comment.getMember().getEmail().equals(email)) {

            // 작성자가 아니면 403 Forbidden 오류 발생
            throw new BusinessException(
                    ErrorCode.COMMENT_ACCESS_DENIED
            );
        }
    }
}
