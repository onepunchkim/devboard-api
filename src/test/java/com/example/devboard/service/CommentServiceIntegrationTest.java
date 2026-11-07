package com.example.devboard.service;

import com.example.devboard.dto.request.CommentCreateRequest;
import com.example.devboard.dto.response.CommentResponse;
import com.example.devboard.entity.Comment;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.repository.CommentRepository;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommentServiceIntegrationTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private EntityManager entityManager;

    // 테스트용 회원 생성
    private Member createTestMember(String email) {
        return memberRepository.save(
                new Member(email, "encoded-test-password", "댓글테스트회원")
        );
    }

    // 테스트용 게시글 생성
    private Post createTestPost(Member member) {
        return postRepository.save(
                new Post("댓글 테스트 게시글", "댓글 테스트 게시글 내용", member)
        );
    }

    // 테스트용 댓글 요청 생성
    private CommentCreateRequest createCommentRequest(String content) {
        return new CommentCreateRequest(content);
    }

    @Test
    void createComment_shouldSaveComment() {
        // given: 회원과 게시글 생성
        Member member = createTestMember("comment1@test.com");
        Post post = createTestPost(member);

        CommentCreateRequest request =
                createCommentRequest("첫 번째 댓글입니다.");

        // when: 댓글 작성
        CommentResponse response = commentService.createComment(
                post.getId(),
                member.getEmail(),
                request
        );

        // DB에 실제 반영
        entityManager.flush();
        entityManager.clear();

        // then: 댓글 저장 여부 확인
        Comment savedComment = commentRepository.findById(response.id())
                .orElseThrow();

        assertThat(savedComment.getContent())
                .isEqualTo("첫 번째 댓글입니다.");

        assertThat(savedComment.getPost().getId())
                .isEqualTo(post.getId());

        assertThat(savedComment.getMember().getId())
                .isEqualTo(member.getId());
    }

    @Test
    void findComments_shouldReturnCommentsByPost() {
        // given: 회원과 게시글 생성
        Member member = createTestMember("comment2@test.com");
        Post post = createTestPost(member);

        commentService.createComment(
                post.getId(),
                member.getEmail(),
                createCommentRequest("첫 번째 댓글")
        );

        commentService.createComment(
                post.getId(),
                member.getEmail(),
                createCommentRequest("두 번째 댓글")
        );

        // when: 게시글의 댓글 목록 조회
        List<CommentResponse> comments =
                commentService.findComments(post.getId());

        // then: 댓글 개수와 내용 확인
        assertThat(comments).hasSize(2);
        assertThat(comments)
                .extracting(CommentResponse::content)
                .containsExactly("첫 번째 댓글", "두 번째 댓글");
    }

    @Test
    void updateComment_shouldUpdateComment() {
        // given: 회원, 게시글, 댓글 생성
        Member member = createTestMember("comment3@test.com");
        Post post = createTestPost(member);

        CommentResponse createdComment = commentService.createComment(
                post.getId(),
                member.getEmail(),
                createCommentRequest("수정 전 댓글")
        );

        // when: 댓글 수정
        CommentResponse updatedComment = commentService.updateComment(
                createdComment.id(),
                member.getEmail(),
                createCommentRequest("수정된 댓글")
        );

        // DB에 실제 반영
        entityManager.flush();
        entityManager.clear();

        // then: 수정된 내용 확인
        Comment savedComment = commentRepository
                .findById(createdComment.id())
                .orElseThrow();

        assertThat(updatedComment.content())
                .isEqualTo("수정된 댓글");

        assertThat(savedComment.getContent())
                .isEqualTo("수정된 댓글");
    }

    @Test
    void deleteComment_shouldDeleteComment() {
        // given: 회원, 게시글, 댓글 생성
        Member member = createTestMember("comment4@test.com");
        Post post = createTestPost(member);

        CommentResponse createdComment = commentService.createComment(
                post.getId(),
                member.getEmail(),
                createCommentRequest("삭제할 댓글")
        );

        Long commentId = createdComment.id();

        // when: 댓글 삭제
        commentService.deleteComment(commentId, member.getEmail());

        // DB에 실제 반영
        entityManager.flush();
        entityManager.clear();

        // then: 댓글이 삭제되었는지 확인
        assertThat(commentRepository.existsById(commentId))
                .isFalse();
    }

    @Test
    void updateComment_shouldRejectOtherMember() {
        // given: 댓글 작성자와 다른 회원 생성
        Member writer = createTestMember("comment5@test.com");
        Member otherMember = createTestMember("other5@test.com");

        Post post = createTestPost(writer);

        CommentResponse createdComment = commentService.createComment(
                post.getId(),
                writer.getEmail(),
                createCommentRequest("작성자가 작성한 댓글")
        );

        // when & then: 다른 회원이 댓글 수정 시도
        assertThatThrownBy(() ->
                commentService.updateComment(
                        createdComment.id(),
                        otherMember.getEmail(),
                        createCommentRequest("다른 회원의 수정 시도")
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.FORBIDDEN
                );

        // 댓글 내용이 변경되지 않았는지 확인
        entityManager.flush();
        entityManager.clear();

        Comment savedComment = commentRepository
                .findById(createdComment.id())
                .orElseThrow();

        assertThat(savedComment.getContent())
                .isEqualTo("작성자가 작성한 댓글");
    }

    @Test
    void deleteComment_shouldRejectOtherMember() {
        // given: 댓글 작성자와 다른 회원 생성
        Member writer = createTestMember("comment6@test.com");
        Member otherMember = createTestMember("other6@test.com");

        Post post = createTestPost(writer);

        CommentResponse createdComment = commentService.createComment(
                post.getId(),
                writer.getEmail(),
                createCommentRequest("삭제하면 안 되는 댓글")
        );

        Long commentId = createdComment.id();

        // when & then: 다른 회원이 댓글 삭제 시도
        assertThatThrownBy(() ->
                commentService.deleteComment(
                        commentId,
                        otherMember.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.FORBIDDEN
                );

        // 댓글이 여전히 존재하는지 확인
        entityManager.flush();
        entityManager.clear();

        assertThat(commentRepository.existsById(commentId))
                .isTrue();
    }


    @Test
    void createComment_shouldRejectMissingPost() {
        // given: 회원은 존재하지만 게시글은 존재하지 않는다.
        Member member = createTestMember("missingpost@test.com");
        Long missingPostId = Long.MAX_VALUE;

        // when & then: 존재하지 않는 게시글에 댓글 작성 시 404
        assertThatThrownBy(() ->
                commentService.createComment(
                        missingPostId,
                        member.getEmail(),
                        createCommentRequest("댓글 작성 시도")
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void createComment_shouldRejectMissingMember() {
        // given: 게시글은 존재하지만 댓글 작성 회원은 존재하지 않는다.
        Member writer = createTestMember("postwriter@test.com");
        Post post = createTestPost(writer);

        // when & then: 존재하지 않는 회원의 댓글 작성 시 404
        assertThatThrownBy(() ->
                commentService.createComment(
                        post.getId(),
                        "missingmember@test.com",
                        createCommentRequest("댓글 작성 시도")
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void findComments_shouldRejectMissingPost() {
        // given: 존재하지 않는 게시글 ID
        Long missingPostId = Long.MAX_VALUE;

        // when & then: 댓글 목록 조회 시 404
        assertThatThrownBy(() ->
                commentService.findComments(missingPostId)
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void findComments_shouldReturnEmptyList() {
        // given: 댓글이 없는 게시글 생성
        Member member = createTestMember("emptycomments@test.com");
        Post post = createTestPost(member);

        // when: 댓글 목록 조회
        List<CommentResponse> comments =
                commentService.findComments(post.getId());

        // then: 정상적으로 빈 목록 반환
        assertThat(comments).isEmpty();
    }

    @Test
    void updateComment_shouldRejectMissingComment() {
        // given: 존재하지 않는 댓글 ID
        Long missingCommentId = Long.MAX_VALUE;

        // when & then: 댓글 수정 시 404
        assertThatThrownBy(() ->
                commentService.updateComment(
                        missingCommentId,
                        "test@test.com",
                        createCommentRequest("수정 시도")
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    void deleteComment_shouldRejectMissingComment() {
        // given: 존재하지 않는 댓글 ID
        Long missingCommentId = Long.MAX_VALUE;

        // when & then: 댓글 삭제 시 404
        assertThatThrownBy(() ->
                commentService.deleteComment(
                        missingCommentId,
                        "test@test.com"
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException =
                            (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

}
