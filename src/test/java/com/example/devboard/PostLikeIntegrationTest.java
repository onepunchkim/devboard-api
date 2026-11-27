package com.example.devboard;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostLike;

import com.example.devboard.exception.ErrorCode;

import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.repository.PostLikeRepository;

import com.example.devboard.service.JwtService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 전체 Spring Context 실행
@SpringBootTest

// 실제 Spring Security 필터를 포함한 MockMvc 설정
@AutoConfigureMockMvc

// 테스트 전용 application-test.yml 사용
@ActiveProfiles("test")

// 테스트 종료 후 데이터 롤백
@Transactional
class PostLikeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    // 좋아요 API 공통 URL
    private static final String LIKE_URL =
            "/api/posts/{postId}/like";

    // 게시글 작성자
    private Member postWriter;

    // 좋아요를 등록할 회원
    private Member likeMember;

    // 다른 일반 회원
    private Member anotherMember;

    // 테스트용 게시글 ID
    private Long postId;

    // 회원별 JWT
    private String postWriterToken;
    private String likeMemberToken;
    private String anotherMemberToken;

    // =====================================================
    // 테스트 데이터 초기화
    // =====================================================

    @BeforeEach
    void setUp() {

        // 게시글 작성자 생성
        postWriter = createTestMember(
                "like-api-writer@test.com",
                "게시글작성자"
        );

        // 좋아요 등록 회원 생성
        likeMember = createTestMember(
                "like-api-member@test.com",
                "좋아요회원"
        );

        // 다른 회원 생성
        anotherMember = createTestMember(
                "like-api-another@test.com",
                "다른회원"
        );

        // 테스트용 게시글 생성
        Post post = createTestPost(
                postWriter,
                "좋아요 API 통합 테스트"
        );

        postId = post.getId();

        // 실제 JwtService를 사용하여 회원별 JWT 발급
        postWriterToken = jwtService.generateToken(
                postWriter.getId(),
                postWriter.getEmail()
        );

        likeMemberToken = jwtService.generateToken(
                likeMember.getId(),
                likeMember.getEmail()
        );

        anotherMemberToken = jwtService.generateToken(
                anotherMember.getId(),
                anotherMember.getEmail()
        );
    }

    // =====================================================
    // 공통 메서드
    // =====================================================

    // 테스트용 회원 생성
    private Member createTestMember(
            String email,
            String nickname
    ) {

        return memberRepository.saveAndFlush(
                new Member(
                        email,
                        passwordEncoder.encode("password123"),
                        nickname
                )
        );
    }

    // 테스트용 게시글 생성
    private Post createTestPost(
            Member member,
            String title
    ) {

        return postRepository.saveAndFlush(
                new Post(
                        title,
                        "좋아요 API 통합 테스트 게시글 내용",
                        member
                )
        );
    }

    // Authorization 헤더용 Bearer 토큰 생성
    private String bearerToken(String token) {

        return "Bearer " + token;
    }

    // SQL 실행 및 영속성 컨텍스트 초기화
    private void flushAndClear() {

        entityManager.flush();
        entityManager.clear();
    }

    // 실제 API를 이용하여 좋아요 등록
    private void registerLike(
            Long targetPostId,
            String token
    ) throws Exception {

        mockMvc.perform(
                        post(LIKE_URL, targetPostId)
                                .header(
                                        "Authorization",
                                        bearerToken(token)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId")
                        .value(targetPostId))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.message")
                        .value("좋아요를 등록했습니다."));
    }

    // 실제 API를 이용하여 좋아요 취소
    private void cancelLike(
            Long targetPostId,
            String token
    ) throws Exception {

        mockMvc.perform(
                        delete(LIKE_URL, targetPostId)
                                .header(
                                        "Authorization",
                                        bearerToken(token)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId")
                        .value(targetPostId))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.message")
                        .value("좋아요를 취소했습니다."));
    }

    // 좋아요 개수 및 회원별 좋아요 상태 검증
    private void assertLikeInfo(
            Long targetPostId,
            String token,
            long expectedCount,
            boolean expectedLiked
    ) throws Exception {

        mockMvc.perform(
                        get(LIKE_URL, targetPostId)
                                .header(
                                        "Authorization",
                                        bearerToken(token)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId")
                        .value(targetPostId))
                .andExpect(jsonPath("$.likeCount")
                        .value(expectedCount))
                .andExpect(jsonPath("$.liked")
                        .value(expectedLiked));
    }

    // =====================================================
    // 1. 좋아요 등록 및 실제 DB 저장 검증
    // =====================================================

    @Test
    void likePost_shouldSaveLikeToDatabase()
            throws Exception {

        // given: 좋아요가 없는 게시글
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isZero();

        // when: 실제 JWT를 사용하여 좋아요 등록
        registerLike(postId, likeMemberToken);

        flushAndClear();

        // then: 실제 DB에서 좋아요 조회
        PostLike savedLike = postLikeRepository
                .findByMemberIdAndPostId(
                        likeMember.getId(),
                        postId
                )
                .orElseThrow(() ->
                        new AssertionError(
                                "좋아요가 저장되지 않았습니다."
                        )
                );

        assertThat(savedLike.getId())
                .isNotNull();

        assertThat(savedLike.getMember().getId())
                .isEqualTo(likeMember.getId());

        assertThat(savedLike.getPost().getId())
                .isEqualTo(postId);

        assertThat(savedLike.getCreatedAt())
                .isNotNull();

        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(1L);
    }

    // =====================================================
    // 2. 좋아요 취소 및 실제 DB 삭제 검증
    // =====================================================

    @Test
    void unlikePost_shouldDeleteLikeFromDatabase()
            throws Exception {

        // given: 기존 좋아요 등록
        registerLike(postId, likeMemberToken);

        // when: 좋아요 취소
        cancelLike(postId, likeMemberToken);

        flushAndClear();

        // then: 좋아요 삭제 여부 확인
        assertThat(
                postLikeRepository.existsByMemberIdAndPostId(
                        likeMember.getId(),
                        postId
                )
        ).isFalse();

        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isZero();

        // 좋아요만 삭제되고 게시글은 유지되어야 함
        assertThat(postRepository.existsById(postId))
                .isTrue();
    }

    // =====================================================
    // 3. 중복 좋아요 등록 방지
    // =====================================================

    @Test
    void likePost_duplicateLike_returnsConflict()
            throws Exception {

        // given: 기존 좋아요 등록
        registerLike(postId, likeMemberToken);

        flushAndClear();

        // when & then: 동일 회원이 다시 좋아요 등록
        mockMvc.perform(
                        post(LIKE_URL, postId)
                                .header(
                                        "Authorization",
                                        bearerToken(likeMemberToken)
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.LIKE_ALREADY_EXISTS.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.LIKE_ALREADY_EXISTS.getMessage()));

        flushAndClear();

        // 중복 요청 이후에도 좋아요는 한 건만 존재
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(1L);
    }

    // =====================================================
    // 4. 등록하지 않은 좋아요 취소
    // =====================================================

    @Test
    void unlikePost_missingLike_returnsNotFound()
            throws Exception {

        // given: 다른 회원만 좋아요 등록
        registerLike(postId, anotherMemberToken);

        // when & then: 좋아요를 등록하지 않은 회원이 취소 시도
        mockMvc.perform(
                        delete(LIKE_URL, postId)
                                .header(
                                        "Authorization",
                                        bearerToken(likeMemberToken)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.LIKE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.LIKE_NOT_FOUND.getMessage()));

        flushAndClear();

        // 다른 회원의 좋아요는 삭제되면 안 됨
        assertThat(
                postLikeRepository.existsByMemberIdAndPostId(
                        anotherMember.getId(),
                        postId
                )
        ).isTrue();

        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(1L);
    }

    // =====================================================
    // 5. 좋아요가 없는 게시글 조회
    // =====================================================

    @Test
    void getLikeInfo_shouldReturnZeroCount()
            throws Exception {

        // given: 좋아요가 없는 게시글

        // when & then: 전체 좋아요 0개, 현재 회원 미등록
        assertLikeInfo(
                postId,
                likeMemberToken,
                0L,
                false
        );
    }

    // =====================================================
    // 6. 여러 회원의 좋아요 등록 및 회원별 상태 검증
    // =====================================================

    @Test
    void getLikeInfo_shouldReturnMemberSpecificStatus()
            throws Exception {

        // given: 두 회원이 같은 게시글에 좋아요 등록
        registerLike(postId, likeMemberToken);
        registerLike(postId, anotherMemberToken);

        flushAndClear();

        // then: 실제 DB 좋아요 개수 검증
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(2L);

        // 좋아요를 등록한 첫 번째 회원
        assertLikeInfo(
                postId,
                likeMemberToken,
                2L,
                true
        );

        // 좋아요를 등록한 두 번째 회원
        assertLikeInfo(
                postId,
                anotherMemberToken,
                2L,
                true
        );

        // 좋아요를 등록하지 않은 게시글 작성자
        assertLikeInfo(
                postId,
                postWriterToken,
                2L,
                false
        );
    }

    // =====================================================
    // 7. 좋아요 취소 후 다른 회원의 좋아요 유지
    // =====================================================

    @Test
    void unlikePost_shouldPreserveOtherMembersLikes()
            throws Exception {

        // given: 두 회원이 좋아요 등록
        registerLike(postId, likeMemberToken);
        registerLike(postId, anotherMemberToken);

        // when: 첫 번째 회원만 좋아요 취소
        cancelLike(postId, likeMemberToken);

        flushAndClear();

        // then: 전체 좋아요는 1개로 감소
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(1L);

        // 취소한 회원의 좋아요 상태
        assertLikeInfo(
                postId,
                likeMemberToken,
                1L,
                false
        );

        // 다른 회원의 좋아요는 유지
        assertLikeInfo(
                postId,
                anotherMemberToken,
                1L,
                true
        );

        assertThat(
                postLikeRepository.existsByMemberIdAndPostId(
                        anotherMember.getId(),
                        postId
                )
        ).isTrue();
    }

    // =====================================================
    // 8. 좋아요 취소 후 재등록
    // =====================================================

    @Test
    void likePost_shouldAllowLikeAgainAfterCancellation()
            throws Exception {

        // given: 좋아요 등록 후 취소
        registerLike(postId, likeMemberToken);
        cancelLike(postId, likeMemberToken);

        flushAndClear();

        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isZero();

        // when: 동일 회원이 다시 좋아요 등록
        registerLike(postId, likeMemberToken);

        flushAndClear();

        // then: 재등록 성공 및 중복 데이터 없음
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(1L);

        assertLikeInfo(
                postId,
                likeMemberToken,
                1L,
                true
        );
    }

    // =====================================================
    // 9. 동일 회원이 서로 다른 게시글에 좋아요 등록
    // =====================================================

    @Test
    void likePost_shouldAllowLikesOnDifferentPosts()
            throws Exception {

        // given: 두 번째 게시글 생성
        Post secondPost = createTestPost(
                postWriter,
                "두 번째 좋아요 테스트 게시글"
        );

        Long secondPostId = secondPost.getId();

        // when: 동일 회원이 서로 다른 게시글에 좋아요 등록
        registerLike(postId, likeMemberToken);
        registerLike(secondPostId, likeMemberToken);

        flushAndClear();

        // then: 각각의 게시글에 좋아요 한 건씩 존재
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(1L);

        assertThat(
                postLikeRepository.countByPostId(secondPostId)
        ).isEqualTo(1L);

        assertThat(
                postLikeRepository.existsByMemberIdAndPostId(
                        likeMember.getId(),
                        postId
                )
        ).isTrue();

        assertThat(
                postLikeRepository.existsByMemberIdAndPostId(
                        likeMember.getId(),
                        secondPostId
                )
        ).isTrue();

        // 게시글별 좋아요 상태 확인
        assertLikeInfo(
                postId,
                likeMemberToken,
                1L,
                true
        );

        assertLikeInfo(
                secondPostId,
                likeMemberToken,
                1L,
                true
        );
    }

    // =====================================================
    // 10. JWT 없이 좋아요 등록 시 401
    // =====================================================

    @Test
    void likePost_withoutToken_returnsUnauthorized()
            throws Exception {

        // when & then: Authorization 헤더 없이 요청
        mockMvc.perform(
                        post(LIKE_URL, postId)
                )
                .andExpect(status().isUnauthorized());

        flushAndClear();

        // 인증되지 않은 요청은 DB에 저장되면 안 됨
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isZero();
    }

    // =====================================================
    // 11. JWT 없이 좋아요 취소 시 401
    // =====================================================

    @Test
    void unlikePost_withoutToken_returnsUnauthorized()
            throws Exception {

        // given: 정상 좋아요 등록
        registerLike(postId, likeMemberToken);

        // when & then: JWT 없이 좋아요 취소 요청
        mockMvc.perform(
                        delete(LIKE_URL, postId)
                )
                .andExpect(status().isUnauthorized());

        flushAndClear();

        // 기존 좋아요는 유지되어야 함
        assertThat(
                postLikeRepository.existsByMemberIdAndPostId(
                        likeMember.getId(),
                        postId
                )
        ).isTrue();

        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(1L);
    }

    // =====================================================
    // 12. JWT 없이 좋아요 정보 조회 시 401
    // =====================================================

    @Test
    void getLikeInfo_withoutToken_returnsUnauthorized()
            throws Exception {

        // when & then: 인증 없이 좋아요 정보 조회
        mockMvc.perform(
                        get(LIKE_URL, postId)
                )
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // 13. 존재하지 않는 게시글의 좋아요 API 요청
    // =====================================================

    @Test
    void likeApis_missingPost_returnsNotFound()
            throws Exception {

        // given: 존재하지 않는 게시글 ID
        Long missingPostId = -1L;

        assertThat(
                postRepository.existsById(missingPostId)
        ).isFalse();

        // when & then: 좋아요 등록 시 404
        mockMvc.perform(
                        post(LIKE_URL, missingPostId)
                                .header(
                                        "Authorization",
                                        bearerToken(likeMemberToken)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.POST_NOT_FOUND.getCode()));

        // 좋아요 취소 시 404
        mockMvc.perform(
                        delete(LIKE_URL, missingPostId)
                                .header(
                                        "Authorization",
                                        bearerToken(likeMemberToken)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.POST_NOT_FOUND.getCode()));

        // 좋아요 정보 조회 시 404
        mockMvc.perform(
                        get(LIKE_URL, missingPostId)
                                .header(
                                        "Authorization",
                                        bearerToken(likeMemberToken)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.POST_NOT_FOUND.getCode()));

        flushAndClear();

        // 잘못된 게시글 ID로 좋아요가 저장되지 않았는지 확인
        assertThat(
                postLikeRepository.countByPostId(missingPostId)
        ).isZero();
    }

    // =====================================================
    // 14. 게시글 삭제 시 관련 좋아요 연쇄 삭제
    // =====================================================

    @Test
    void deletePost_shouldDeleteRelatedLikes()
            throws Exception {

        // given: 서로 다른 두 회원이 좋아요 등록
        registerLike(postId, likeMemberToken);
        registerLike(postId, anotherMemberToken);

        // 삭제 대상이 아닌 다른 게시글 생성
        Post otherPost = createTestPost(
                postWriter,
                "삭제되지 않아야 하는 게시글"
        );

        Long otherPostId = otherPost.getId();

        // 다른 게시글에도 좋아요 등록
        registerLike(otherPostId, likeMemberToken);

        flushAndClear();

        // 게시글 삭제 전 좋아요 개수 검증
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isEqualTo(2L);

        assertThat(
                postLikeRepository.countByPostId(otherPostId)
        ).isEqualTo(1L);

        // when: 게시글 작성자가 게시글 삭제
        mockMvc.perform(
                        delete("/api/posts/{id}", postId)
                                .header(
                                        "Authorization",
                                        bearerToken(postWriterToken)
                                )
                )
                .andExpect(status().isNoContent());

        flushAndClear();

        // then: 게시글 삭제 확인
        assertThat(
                postRepository.existsById(postId)
        ).isFalse();

        // 삭제된 게시글의 좋아요가 모두 삭제되었는지 확인
        assertThat(
                postLikeRepository.countByPostId(postId)
        ).isZero();

        // 다른 게시글과 좋아요는 유지되어야 함
        assertThat(
                postRepository.existsById(otherPostId)
        ).isTrue();

        assertThat(
                postLikeRepository.countByPostId(otherPostId)
        ).isEqualTo(1L);

        assertThat(
                postLikeRepository.existsByMemberIdAndPostId(
                        likeMember.getId(),
                        otherPostId
                )
        ).isTrue();

        // 관련 회원 정보는 삭제되면 안 됨
        assertThat(
                memberRepository.existsById(postWriter.getId())
        ).isTrue();

        assertThat(
                memberRepository.existsById(likeMember.getId())
        ).isTrue();

        assertThat(
                memberRepository.existsById(anotherMember.getId())
        ).isTrue();
    }
}
