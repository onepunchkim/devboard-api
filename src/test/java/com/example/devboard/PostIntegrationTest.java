package com.example.devboard;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.service.PostService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    // DB에 SQL을 반영하고 영속성 컨텍스트를 초기화하기 위해 사용
    @Autowired
    private EntityManager entityManager;

    // 테스트용 회원을 생성하고 DB에 저장하는 메서드
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
        // given: 테스트용 회원과 게시글 정보 준비
        String email = "post-create-test@example.com";
        createTestMember(email);

        String title = "통합 테스트 제목";
        String content = "통합 테스트 내용";

        // when: 게시글 작성 서비스 실행
        PostResponse response =
                postService.createPost(title, content, email);

        // INSERT SQL 실행
        entityManager.flush();

        // 영속성 컨텍스트를 비워 DB에서 다시 조회하도록 설정
        entityManager.clear();

        // then: DB에 저장된 게시글 조회
        Post savedPost = postRepository.findById(response.id())
                .orElseThrow(() ->
                        new AssertionError("게시글이 DB에 저장되지 않았습니다."));

        // 게시글 제목과 내용 확인
        assertEquals(title, savedPost.getTitle());
        assertEquals(content, savedPost.getContent());

        // 게시글 작성자가 올바르게 연결되었는지 확인
        assertEquals(email, savedPost.getMember().getEmail());
    }

    // 2. 게시글 상세 조회 테스트
    @Test
    void findById_shouldReturnSavedPost() {
        // given: 회원과 게시글 생성
        String email = "post-find-test@example.com";
        createTestMember(email);

        PostResponse created = postService.createPost(
                "조회 테스트 제목",
                "조회 테스트 내용",
                email
        );

        // when: 게시글 상세 조회
        PostResponse response = postService.findById(created.id());

        // then: 조회 결과 확인
        assertEquals(created.id(), response.id());
        assertEquals("조회 테스트 제목", response.title());
        assertEquals("조회 테스트 내용", response.content());
    }

    // 3. 게시글 수정 및 DB 반영 테스트
    @Test
    void updatePost_shouldUpdatePostInDatabase() {
        // given: 회원과 게시글 생성
        String email = "post-update-test@example.com";
        createTestMember(email);

        PostResponse created = postService.createPost(
                "수정 전 제목",
                "수정 전 내용",
                email
        );

        // when: 작성자가 게시글 수정
        postService.updatePost(
                created.id(),
                "수정 후 제목",
                "수정 후 내용",
                email
        );

        // UPDATE SQL 실행 후 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // then: DB에서 다시 조회하여 수정 결과 확인
        Post updatedPost = postRepository.findById(created.id())
                .orElseThrow(() ->
                        new AssertionError("수정한 게시글을 찾을 수 없습니다."));

        assertEquals("수정 후 제목", updatedPost.getTitle());
        assertEquals("수정 후 내용", updatedPost.getContent());
    }

    // 4. 게시글 삭제 테스트
    @Test
    void deletePost_shouldRemovePostFromDatabase() {
        // given: 회원과 게시글 생성
        String email = "post-delete-test@example.com";
        createTestMember(email);

        PostResponse created = postService.createPost(
                "삭제 테스트 제목",
                "삭제 테스트 내용",
                email
        );

        Long postId = created.id();

        // when: 작성자가 게시글 삭제
        postService.deletePost(postId, email);

        // DELETE SQL 실행 후 영속성 컨텍스트 초기화
        entityManager.flush();
        entityManager.clear();

        // then: DB에서 게시글이 삭제되었는지 확인
        assertFalse(postRepository.existsById(postId));
    }

    // 5. 다른 회원의 게시글 수정 차단 테스트
    @Test
    void updatePost_shouldRejectOtherMember() {
        // given: 게시글 작성자와 다른 회원 생성
        String ownerEmail = "post-owner-test@example.com";
        String otherEmail = "post-other-test@example.com";

        createTestMember(ownerEmail);
        createTestMember(otherEmail);

        PostResponse created = postService.createPost(
                "원래 제목",
                "원래 내용",
                ownerEmail
        );

        // when: 다른 회원이 수정을 시도하면 예외가 발생해야 함
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.updatePost(
                        created.id(),
                        "변경 시도 제목",
                        "변경 시도 내용",
                        otherEmail
                )
        );

        // then: HTTP 403 FORBIDDEN인지 확인
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

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
        // given: 게시글 작성자와 다른 회원 생성
        String ownerEmail = "post-delete-owner@example.com";
        String otherEmail = "post-delete-other@example.com";

        createTestMember(ownerEmail);
        createTestMember(otherEmail);

        // 게시글 작성자가 게시글 생성
        PostResponse created = postService.createPost(
                "삭제 권한 테스트 제목",
                "삭제 권한 테스트 내용",
                ownerEmail
        );

        Long postId = created.id();

        // when & then: 다른 회원이 삭제를 시도하면 예외가 발생해야 함
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.deletePost(postId, otherEmail)
        );

        // 다른 회원의 삭제 요청은 HTTP 403이어야 함
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        // DB에서 게시글이 여전히 존재하는지 확인
        entityManager.flush();
        entityManager.clear();

        Post savedPost = postRepository.findById(postId)
                .orElseThrow(() ->
                        new AssertionError("게시글이 삭제되면 안 됩니다."));

        // 게시글의 제목과 내용이 유지되는지 확인
        assertEquals("삭제 권한 테스트 제목", savedPost.getTitle());
        assertEquals("삭제 권한 테스트 내용", savedPost.getContent());
    }
}