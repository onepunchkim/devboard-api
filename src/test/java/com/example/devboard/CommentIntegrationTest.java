package com.example.devboard;

import com.example.devboard.dto.request.CommentCreateRequest;
import com.example.devboard.dto.response.CommentResponse;

import com.example.devboard.entity.Comment;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;

import com.example.devboard.exception.ErrorCode;

import com.example.devboard.repository.CommentRepository;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;

import com.example.devboard.service.CommentService;
import com.example.devboard.service.JwtService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 전체 Spring Context를 실행하여 실제 Bean 사용
@SpringBootTest

// MockMvc 및 Spring Security 필터 적용
@AutoConfigureMockMvc

// application-test.yml 설정 사용
@ActiveProfiles("test")

// 테스트 종료 후 테스트 트랜잭션 롤백
@Transactional
class CommentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentService commentService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    // 게시글 작성자
    private Member postWriter;

    // 댓글 작성자
    private Member commentWriter;

    // 게시글 및 댓글 작성자가 아닌 일반 회원
    private Member anotherMember;

    // 테스트용 게시글 ID
    private Long postId;

    // 각 회원의 JWT
    private String postWriterToken;
    private String commentWriterToken;
    private String anotherMemberToken;

    // =====================================================
    // 테스트 데이터 준비
    // =====================================================

    @BeforeEach
    void setUp() {

        // 게시글 작성자 생성
        postWriter = createTestMember(
                "comment-post-writer@test.com",
                "게시글작성자"
        );

        // 댓글 작성자 생성
        commentWriter = createTestMember(
                "comment-writer@test.com",
                "댓글작성자"
        );

        // 다른 일반 회원 생성
        anotherMember = createTestMember(
                "comment-another@test.com",
                "다른회원"
        );

        // 게시글 작성자 명의의 게시글 생성
        Post post = postRepository.saveAndFlush(
                new Post(
                        "댓글 API 통합 테스트 게시글",
                        "댓글 API 통합 테스트 내용",
                        postWriter
                )
        );

        postId = post.getId();

        // 실제 JwtService를 사용하여 JWT 발급
        postWriterToken = jwtService.generateToken(
                postWriter.getId(),
                postWriter.getEmail()
        );

        commentWriterToken = jwtService.generateToken(
                commentWriter.getId(),
                commentWriter.getEmail()
        );

        anotherMemberToken = jwtService.generateToken(
                anotherMember.getId(),
                anotherMember.getEmail()
        );
    }

    // =====================================================
    // 공통 메서드
    // =====================================================

    // 테스트용 회원 생성 및 DB 저장
    private Member createTestMember(
            String email,
            String nickname
    ) {

        Member member = new Member(
                email,
                passwordEncoder.encode("password123"),
                nickname
        );

        return memberRepository.saveAndFlush(member);
    }

    // Authorization 헤더에 사용할 Bearer 토큰 생성
    private String bearerToken(String token) {

        return "Bearer " + token;
    }

    // 테스트 준비 단계에서 실제 Service를 이용해 댓글 생성
    private CommentResponse createTestComment(
            Member member,
            String content
    ) {

        return commentService.createComment(
                postId,
                member.getEmail(),
                new CommentCreateRequest(content)
        );
    }

    // SQL 실행 및 영속성 컨텍스트 초기화
    private void flushAndClear() {

        entityManager.flush();
        entityManager.clear();
    }

    // DB에서 댓글을 다시 조회
    private Comment findSavedComment(Long commentId) {

        return commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new AssertionError(
                                "댓글을 DB에서 찾을 수 없습니다."
                        )
                );
    }

    // =====================================================
    // 1. 정상 JWT를 이용한 댓글 작성 및 DB 저장
    // =====================================================

    @Test
    void createComment_shouldSaveCommentToDatabase()
            throws Exception {

        // given: 정상 댓글 작성 요청
        String requestBody = """
                {
                    "content": "API 통합 테스트 댓글입니다."
                }
                """;

        // when & then: 실제 JWT를 이용해 댓글 작성
        mockMvc.perform(
                        post("/api/posts/{postId}/comments", postId)
                                .header(
                                        "Authorization",
                                        bearerToken(commentWriterToken)
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.content")
                        .value("API 통합 테스트 댓글입니다."))
                .andExpect(jsonPath("$.postId")
                        .value(postId))
                .andExpect(jsonPath("$.memberId")
                        .value(commentWriter.getId()))
                .andExpect(jsonPath("$.nickname")
                        .value("댓글작성자"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        // 실제 DB 반영
        flushAndClear();

        // 해당 게시글에 저장된 댓글 조회
        var comments =
                commentRepository.findByPostIdOrderByCreatedAtAsc(
                        postId
                );

        assertThat(comments).hasSize(1);

        Comment savedComment = comments.get(0);

        // 실제 댓글 내용 및 작성자 확인
        assertThat(savedComment.getContent())
                .isEqualTo("API 통합 테스트 댓글입니다.");

        assertThat(savedComment.getPost().getId())
                .isEqualTo(postId);

        assertThat(savedComment.getMember().getId())
                .isEqualTo(commentWriter.getId());
    }

    // =====================================================
    // 2. 댓글 작성 시 앞뒤 공백 제거
    // =====================================================

    @Test
    void createComment_shouldTrimContent()
            throws Exception {

        // given: 앞뒤 공백이 포함된 댓글
        String requestBody = """
                {
                    "content": "   공백 제거 테스트   "
                }
                """;

        // when & then: 공백이 제거된 응답 확인
        mockMvc.perform(
                        post("/api/posts/{postId}/comments", postId)
                                .header(
                                        "Authorization",
                                        bearerToken(commentWriterToken)
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content")
                        .value("공백 제거 테스트"));

        flushAndClear();

        var comments =
                commentRepository.findByPostIdOrderByCreatedAtAsc(
                        postId
                );

        // DB에도 공백이 제거된 내용이 저장되었는지 확인
        assertThat(comments).hasSize(1);

        assertThat(comments.get(0).getContent())
                .isEqualTo("공백 제거 테스트");
    }

    // =====================================================
    // 3. 댓글 목록 조회
    // =====================================================

    @Test
    void findComments_shouldReturnSavedComments()
            throws Exception {

        // given: 서로 다른 회원이 댓글 작성
        CommentResponse firstComment = createTestComment(
                commentWriter,
                "첫 번째 댓글"
        );

        CommentResponse secondComment = createTestComment(
                anotherMember,
                "두 번째 댓글"
        );

        flushAndClear();

        // when & then: 댓글 목록 및 작성자 확인
        mockMvc.perform(
                        get("/api/posts/{postId}/comments", postId)
                                .header(
                                        "Authorization",
                                        bearerToken(commentWriterToken)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                firstComment.id().intValue(),
                                secondComment.id().intValue()
                        )))
                .andExpect(jsonPath("$[*].content",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                "첫 번째 댓글",
                                "두 번째 댓글"
                        )))
                .andExpect(jsonPath("$[*].nickname",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                "댓글작성자",
                                "다른회원"
                        )));
    }

    // =====================================================
    // 4. 댓글이 없는 게시글 조회
    // =====================================================

    @Test
    void findComments_shouldReturnEmptyList()
            throws Exception {

        // given: 댓글이 없는 게시글

        // when & then: 빈 JSON 배열 반환
        mockMvc.perform(
                        get("/api/posts/{postId}/comments", postId)
                                .header(
                                        "Authorization",
                                        bearerToken(commentWriterToken)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // =====================================================
    // 5. 댓글 수정 및 DB 반영
    // =====================================================

    @Test
    void updateComment_shouldUpdateCommentInDatabase()
            throws Exception {

        // given: 기존 댓글 생성
        CommentResponse createdComment = createTestComment(
                commentWriter,
                "수정 전 댓글"
        );

        Long commentId = createdComment.id();

        String requestBody = """
                {
                    "content": "   수정된 댓글입니다.   "
                }
                """;

        // when & then: 댓글 작성자가 수정 요청
        mockMvc.perform(
                        put("/api/comments/{commentId}", commentId)
                                .header(
                                        "Authorization",
                                        bearerToken(commentWriterToken)
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId))
                .andExpect(jsonPath("$.content")
                        .value("수정된 댓글입니다."))
                .andExpect(jsonPath("$.memberId")
                        .value(commentWriter.getId()));

        flushAndClear();

        // 실제 DB에서 수정된 댓글 조회
        Comment savedComment = findSavedComment(commentId);

        assertThat(savedComment.getContent())
                .isEqualTo("수정된 댓글입니다.");

        // 수정 이후에도 기존 작성자가 유지되는지 확인
        assertThat(savedComment.getMember().getId())
                .isEqualTo(commentWriter.getId());
    }

    // =====================================================
    // 6. 댓글 삭제 및 DB 반영
    // =====================================================

    @Test
    void deleteComment_shouldRemoveCommentFromDatabase()
            throws Exception {

        // given: 삭제할 댓글 생성
        CommentResponse createdComment = createTestComment(
                commentWriter,
                "삭제할 댓글"
        );

        Long commentId = createdComment.id();

        // when & then: 댓글 삭제 요청
        mockMvc.perform(
                        delete("/api/comments/{commentId}", commentId)
                                .header(
                                        "Authorization",
                                        bearerToken(commentWriterToken)
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        flushAndClear();

        // 댓글 삭제 여부 확인
        assertThat(commentRepository.existsById(commentId))
                .isFalse();

        // 댓글만 삭제되고 게시글은 유지되어야 함
        assertThat(postRepository.existsById(postId))
                .isTrue();
    }

    // =====================================================
    // 7. JWT 없이 댓글 작성 시 401
    // =====================================================

    @Test
    void createComment_withoutToken_returnsUnauthorized()
            throws Exception {

        // given: JWT가 없는 댓글 작성 요청
        String requestBody = """
                {
                    "content": "인증 없이 작성 시도"
                }
                """;

        // when & then: 인증 실패
        mockMvc.perform(
                        post("/api/posts/{postId}/comments", postId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnauthorized());

        flushAndClear();

        // 인증되지 않은 댓글이 저장되지 않았는지 확인
        assertThat(
                commentRepository.findByPostIdOrderByCreatedAtAsc(
                        postId
                )
        ).isEmpty();
    }

    // =====================================================
    // 8. JWT 없이 댓글 수정 시 401
    // =====================================================

    @Test
    void updateComment_withoutToken_returnsUnauthorized()
            throws Exception {

        // given: 기존 댓글 생성
        CommentResponse createdComment = createTestComment(
                commentWriter,
                "기존 댓글"
        );

        Long commentId = createdComment.id();

        // when & then: JWT 없이 수정 요청
        mockMvc.perform(
                        put("/api/comments/{commentId}", commentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "content": "인증 없는 수정"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        flushAndClear();

        // 기존 댓글 내용이 유지되는지 확인
        assertThat(findSavedComment(commentId).getContent())
                .isEqualTo("기존 댓글");
    }

    // =====================================================
    // 9. JWT 없이 댓글 삭제 시 401
    // =====================================================

    @Test
    void deleteComment_withoutToken_returnsUnauthorized()
            throws Exception {

        // given: 기존 댓글 생성
        CommentResponse createdComment = createTestComment(
                commentWriter,
                "삭제되면 안 되는 댓글"
        );

        Long commentId = createdComment.id();

        // when & then: JWT 없이 삭제 요청
        mockMvc.perform(
                        delete("/api/comments/{commentId}", commentId)
                )
                .andExpect(status().isUnauthorized());

        flushAndClear();

        // 기존 댓글이 유지되는지 확인
        assertThat(commentRepository.existsById(commentId))
                .isTrue();
    }

    // =====================================================
    // 10. 다른 회원의 댓글 수정 시 403
    // =====================================================

    @Test
    void updateComment_otherMember_returnsForbidden()
            throws Exception {

        // given: 댓글 작성자의 댓글 생성
        CommentResponse createdComment = createTestComment(
                commentWriter,
                "작성자의 원래 댓글"
        );

        Long commentId = createdComment.id();

        // when & then: 다른 회원이 댓글 수정 시도
        mockMvc.perform(
                        put("/api/comments/{commentId}", commentId)
                                .header(
                                        "Authorization",
                                        bearerToken(anotherMemberToken)
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "content": "다른 회원의 수정 시도"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.COMMENT_ACCESS_DENIED.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.COMMENT_ACCESS_DENIED.getMessage()));

        flushAndClear();

        // 권한 위반 이후에도 기존 내용이 유지되어야 함
        assertThat(findSavedComment(commentId).getContent())
                .isEqualTo("작성자의 원래 댓글");
    }

    // =====================================================
    // 11. 다른 회원의 댓글 삭제 시 403
    // =====================================================

    @Test
    void deleteComment_otherMember_returnsForbidden()
            throws Exception {

        // given: 댓글 작성자의 댓글 생성
        CommentResponse createdComment = createTestComment(
                commentWriter,
                "다른 회원이 삭제할 수 없는 댓글"
        );

        Long commentId = createdComment.id();

        // when & then: 다른 회원이 댓글 삭제 시도
        mockMvc.perform(
                        delete("/api/comments/{commentId}", commentId)
                                .header(
                                        "Authorization",
                                        bearerToken(anotherMemberToken)
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.COMMENT_ACCESS_DENIED.getCode()));

        flushAndClear();

        // 댓글이 삭제되지 않았는지 확인
        assertThat(commentRepository.existsById(commentId))
                .isTrue();
    }

    // =====================================================
    // 12. 게시글 작성자의 타인 댓글 삭제 시 403
    // =====================================================

    @Test
    void deleteComment_postWriterCannotDeleteOthersComment()
            throws Exception {

        // given: 게시글 작성자가 아닌 회원이 댓글 작성
        CommentResponse createdComment = createTestComment(
                commentWriter,
                "게시글 작성자도 직접 삭제할 수 없는 댓글"
        );

        Long commentId = createdComment.id();

        // when & then: 게시글 작성자가 다른 회원의 댓글 삭제 시도
        mockMvc.perform(
                        delete("/api/comments/{commentId}", commentId)
                                .header(
                                        "Authorization",
                                        bearerToken(postWriterToken)
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.COMMENT_ACCESS_DENIED.getCode()));

        flushAndClear();

        // 댓글 작성자가 아니므로 댓글이 유지되어야 함
        assertThat(commentRepository.existsById(commentId))
                .isTrue();
    }

    // =====================================================
    // 13. 존재하지 않는 게시글에 댓글 작성 시 404
    // =====================================================

    @Test
    void createComment_missingPost_returnsNotFound()
            throws Exception {

        // given: 존재하지 않는 게시글 ID
        Long missingPostId = Long.MAX_VALUE;

        // when & then: 존재하지 않는 게시글에 댓글 작성
        mockMvc.perform(
                        post(
                                "/api/posts/{postId}/comments",
                                missingPostId
                        )
                                .header(
                                        "Authorization",
                                        bearerToken(commentWriterToken)
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "content": "댓글 작성 시도"
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.POST_NOT_FOUND.getMessage()));
    }

    // =====================================================
    // 14. 잘못된 댓글 입력 시 400 및 DB 저장 방지
    // =====================================================

    @Test
    void createComment_invalidContent_returnsBadRequest()
            throws Exception {

        // given: 검증에 실패해야 하는 댓글 요청 목록
        String[] invalidRequests = {
                """
                {"content": ""}
                """,
                """
                {"content": "   "}
                """,
                """
                {"content": null}
                """,
                "{}",
                """
                {"content": "%s"}
                """.formatted("가".repeat(1001))
        };

        // when & then: 모든 잘못된 입력에 대해 HTTP 400 검증
        for (String requestBody : invalidRequests) {

            mockMvc.perform(
                            post(
                                    "/api/posts/{postId}/comments",
                                    postId
                            )
                                    .header(
                                            "Authorization",
                                            bearerToken(commentWriterToken)
                                    )
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors.content").exists());
        }

        flushAndClear();

        // 검증에 실패한 댓글은 DB에 저장되면 안 됨
        assertThat(
                commentRepository.findByPostIdOrderByCreatedAtAsc(
                        postId
                )
        ).isEmpty();
    }

    // =====================================================
    // 15. 게시글 삭제 시 관련 댓글 연쇄 삭제
    // =====================================================

    @Test
    void deletePost_shouldDeleteRelatedComments()
            throws Exception {

        // given: 서로 다른 회원의 댓글 생성
        CommentResponse firstComment = createTestComment(
                commentWriter,
                "첫 번째 삭제 대상 댓글"
        );

        CommentResponse secondComment = createTestComment(
                anotherMember,
                "두 번째 삭제 대상 댓글"
        );

        Long firstCommentId = firstComment.id();
        Long secondCommentId = secondComment.id();

        // 실제 DB에 댓글 저장
        flushAndClear();

        // 게시글 삭제 전 댓글 존재 여부 확인
        assertThat(commentRepository.existsById(firstCommentId))
                .isTrue();

        assertThat(commentRepository.existsById(secondCommentId))
                .isTrue();

        // when & then: 게시글 작성자가 게시글 삭제
        mockMvc.perform(
                        delete("/api/posts/{id}", postId)
                                .header(
                                        "Authorization",
                                        bearerToken(postWriterToken)
                                )
                )
                .andExpect(status().isNoContent());

        flushAndClear();

        // 게시글 삭제 확인
        assertThat(postRepository.existsById(postId))
                .isFalse();

        // 게시글에 속한 댓글도 모두 삭제되었는지 확인
        assertThat(commentRepository.existsById(firstCommentId))
                .isFalse();

        assertThat(commentRepository.existsById(secondCommentId))
                .isFalse();

        // 게시글에 연결된 댓글이 남아 있지 않은지 확인
        assertThat(
                commentRepository.findByPostIdOrderByCreatedAtAsc(
                        postId
                )
        ).isEmpty();

        // 게시글 및 댓글 작성자의 회원 정보는 유지되어야 함
        assertThat(memberRepository.existsById(postWriter.getId()))
                .isTrue();

        assertThat(memberRepository.existsById(commentWriter.getId()))
                .isTrue();

        assertThat(memberRepository.existsById(anotherMember.getId()))
                .isTrue();
    }
}
