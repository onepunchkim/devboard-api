package com.example.devboard.service;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.exception.BusinessException;
import com.example.devboard.exception.ErrorCode;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.service.PostService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostServiceIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    // SQL을 DB에 반영하고 영속성 컨텍스트를 초기화할 때 사용
    @Autowired
    private EntityManager entityManager;

    // 테스트용 회원 생성 및 저장
    private Member createTestMember(String email) {
        Member member = new Member(
                email,
                "encoded-test-password",
                "테스트회원"
        );

        return memberRepository.save(member);
    }

    // 1. 게시글 작성 및 DB 저장 테스트
    @Test
    void createPost_shouldSavePostToDatabase() {

        // given
        String email = "post-create-test@example.com";
        createTestMember(email);

        String title = "통합 테스트 제목";
        String content = "통합 테스트 내용";

        // when
        PostResponse response =
                postService.createPost(title, content, email);

        // INSERT SQL 실행
        entityManager.flush();

        // DB에서 다시 조회하도록 영속성 컨텍스트 초기화
        entityManager.clear();

        // then
        Post savedPost = postRepository.findById(response.id())
                .orElseThrow(() ->
                        new AssertionError("게시글이 DB에 저장되지 않았습니다."));

        assertEquals(title, savedPost.getTitle());
        assertEquals(content, savedPost.getContent());
        assertEquals(email, savedPost.getMember().getEmail());
    }

    // 2. 게시글 상세 조회 테스트
    @Test
    void findById_shouldReturnSavedPost() {

        // given
        String email = "post-find-test@example.com";
        createTestMember(email);

        PostResponse created = postService.createPost(
                "조회 테스트 제목",
                "조회 테스트 내용",
                email
        );

        // when
        PostResponse response = postService.findById(created.id());

        // then
        assertEquals(created.id(), response.id());
        assertEquals("조회 테스트 제목", response.title());
        assertEquals("조회 테스트 내용", response.content());
    }

    // 3. 게시글 수정 및 DB 반영 테스트
    @Test
    void updatePost_shouldUpdatePostInDatabase() {

        // given
        String email = "post-update-test@example.com";
        createTestMember(email);

        PostResponse created = postService.createPost(
                "수정 전 제목",
                "수정 전 내용",
                email
        );

        // when
        postService.updatePost(
                created.id(),
                "수정 후 제목",
                "수정 후 내용",
                email
        );

        // UPDATE SQL 실행
        entityManager.flush();
        entityManager.clear();

        // then
        Post updatedPost = postRepository.findById(created.id())
                .orElseThrow(() ->
                        new AssertionError("수정한 게시글을 찾을 수 없습니다."));

        assertEquals("수정 후 제목", updatedPost.getTitle());
        assertEquals("수정 후 내용", updatedPost.getContent());
    }

    // 4. 게시글 삭제 테스트
    @Test
    void deletePost_shouldRemovePostFromDatabase() {

        // given
        String email = "post-delete-test@example.com";
        createTestMember(email);

        PostResponse created = postService.createPost(
                "삭제 테스트 제목",
                "삭제 테스트 내용",
                email
        );

        Long postId = created.id();

        // when
        postService.deletePost(postId, email);

        // DELETE SQL 실행
        entityManager.flush();
        entityManager.clear();

        // then
        assertFalse(postRepository.existsById(postId));
    }

    // 5. 다른 회원의 게시글 수정 차단 테스트
    @Test
    void updatePost_shouldRejectOtherMember() {

        // given: 작성자와 다른 회원 생성
        String ownerEmail = "post-owner-test@example.com";
        String otherEmail = "post-other-test@example.com";

        createTestMember(ownerEmail);
        createTestMember(otherEmail);

        PostResponse created = postService.createPost(
                "원래 제목",
                "원래 내용",
                ownerEmail
        );

        // when: 다른 회원이 게시글 수정을 시도
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postService.updatePost(
                        created.id(),
                        "변경 시도 제목",
                        "변경 시도 내용",
                        otherEmail
                )
        );

        // then: 작성자 권한 오류 코드 확인
        assertEquals(
                ErrorCode.POST_ACCESS_DENIED,
                exception.getErrorCode()
        );

        // ErrorCode에 정의된 HTTP 상태가 403인지 확인
        assertEquals(
                403,
                exception.getErrorCode().getStatus().value()
        );

        // DB에서 다시 조회해 원래 내용이 유지되는지 확인
        entityManager.flush();
        entityManager.clear();

        Post savedPost = postRepository.findById(created.id())
                .orElseThrow();

        assertEquals("원래 제목", savedPost.getTitle());
        assertEquals("원래 내용", savedPost.getContent());
    }

    // 6. 다른 회원의 게시글 삭제 차단 테스트
    @Test
    void deletePost_shouldRejectOtherMember() {

        // given: 작성자와 다른 회원 생성
        String ownerEmail = "post-delete-owner@example.com";
        String otherEmail = "post-delete-other@example.com";

        createTestMember(ownerEmail);
        createTestMember(otherEmail);

        PostResponse created = postService.createPost(
                "삭제 권한 테스트 제목",
                "삭제 권한 테스트 내용",
                ownerEmail
        );

        Long postId = created.id();

        // when: 다른 회원이 삭제를 시도
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postService.deletePost(postId, otherEmail)
        );

        // then: 작성자 권한 오류 코드 확인
        assertEquals(
                ErrorCode.POST_ACCESS_DENIED,
                exception.getErrorCode()
        );

        // HTTP 상태가 403인지 확인
        assertEquals(
                403,
                exception.getErrorCode().getStatus().value()
        );

        // DB에서 게시글이 여전히 존재하는지 확인
        entityManager.flush();
        entityManager.clear();

        Post savedPost = postRepository.findById(postId)
                .orElseThrow(() ->
                        new AssertionError("게시글이 삭제되면 안 됩니다."));

        // 원래 데이터가 유지되었는지 확인
        assertEquals("삭제 권한 테스트 제목", savedPost.getTitle());
        assertEquals("삭제 권한 테스트 내용", savedPost.getContent());
    }
}