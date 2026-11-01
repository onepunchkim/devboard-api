package com.example.devboard;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostLike;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostLikeRepository;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.service.PostLikeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostLikeServiceIntegrationTest {

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private EntityManager entityManager;

    // 테스트용 회원 생성
    private Member createTestMember(String email) {
        return memberRepository.save(
                new Member(email, "encoded-test-password", "좋아요테스트회원")
        );
    }

    // 테스트용 게시글 생성
    private Post createTestPost(Member member, String title) {
        return postRepository.save(
                new Post(title, "좋아요 테스트 게시글 내용", member)
        );
    }

    @Test
    void likePost_shouldSaveLike() {
        // given: 회원과 게시글 생성
        Member member = createTestMember("like1@test.com");
        Post post = createTestPost(member, "좋아요 테스트 게시글");

        // when: 좋아요 등록
        postLikeService.likePost(member.getId(), post.getId());

        entityManager.flush();
        entityManager.clear();

        // then: DB에 좋아요가 저장되었는지 확인
        List<PostLike> likes = postLikeRepository.findAll();

        assertThat(likes).hasSize(1);
        assertThat(likes.get(0).getMember().getId())
                .isEqualTo(member.getId());
        assertThat(likes.get(0).getPost().getId())
                .isEqualTo(post.getId());
    }

    @Test
    void likePost_shouldRejectDuplicateLike() {
        // given: 회원과 게시글 생성 후 좋아요 등록
        Member member = createTestMember("like2@test.com");
        Post post = createTestPost(member, "중복 좋아요 테스트");

        postLikeService.likePost(member.getId(), post.getId());

        // when & then: 같은 회원이 다시 좋아요 등록 시도
        assertThatThrownBy(() ->
                postLikeService.likePost(member.getId(), post.getId())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이미 좋아요를 누른 게시글 입니다.");

        assertThat(postLikeRepository.countByPostId(post.getId()))
                .isEqualTo(1);
    }

    @Test
    void unlikePost_shouldDeleteLike() {
        // given: 좋아요 등록
        Member member = createTestMember("like3@test.com");
        Post post = createTestPost(member, "좋아요 취소 테스트");

        postLikeService.likePost(member.getId(), post.getId());

        // when: 좋아요 취소
        postLikeService.unlikePost(member.getId(), post.getId());

        entityManager.flush();
        entityManager.clear();

        // then: 좋아요 기록 삭제 확인
        assertThat(postLikeRepository.countByPostId(post.getId()))
                .isZero();
    }

    @Test
    void getLikeCount_shouldReturnCount() {
        // given: 두 회원과 게시글 생성
        Member member1 = createTestMember("like4@test.com");
        Member member2 = createTestMember("like5@test.com");

        Post post = createTestPost(member1, "좋아요 개수 테스트");

        postLikeService.likePost(member1.getId(), post.getId());
        postLikeService.likePost(member2.getId(), post.getId());

        // when: 좋아요 개수 조회
        long count = postLikeService.getLikeCount(post.getId());

        // then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void getLikeCounts_shouldReturnCountsForMultiplePosts() {
        // given: 회원과 게시글 두 개 생성
        Member member1 = createTestMember("like6@test.com");
        Member member2 = createTestMember("like7@test.com");

        Post post1 = createTestPost(member1, "첫 번째 게시글");
        Post post2 = createTestPost(member1, "두 번째 게시글");

        // 첫 번째 게시글에는 좋아요 2개
        postLikeService.likePost(member1.getId(), post1.getId());
        postLikeService.likePost(member2.getId(), post1.getId());

        // 두 번째 게시글에는 좋아요 1개
        postLikeService.likePost(member1.getId(), post2.getId());

        // when: 여러 게시글의 좋아요 개수 일괄 조회
        Map<Long, Long> counts = postLikeService.getLikeCounts(
                List.of(post1.getId(), post2.getId())
        );

        // then
        assertThat(counts)
                .containsEntry(post1.getId(), 2L)
                .containsEntry(post2.getId(), 1L);
    }

    @Test
    void hasLiked_shouldReturnLikeStatus() {
        // given: 회원과 게시글 생성
        Member member = createTestMember("like8@test.com");
        Post post = createTestPost(member, "좋아요 여부 테스트");

        // 좋아요 등록 전에는 false
        assertThat(postLikeService.hasLiked(member.getId(), post.getId()))
                .isFalse();

        // when: 좋아요 등록
        postLikeService.likePost(member.getId(), post.getId());

        // then: 좋아요 등록 후에는 true
        assertThat(postLikeService.hasLiked(member.getId(), post.getId()))
                .isTrue();
    }

    @Test
    void emailBasedLikeMethods_shouldWork() {
        // given: 회원과 게시글 생성
        Member member = createTestMember("like9@test.com");
        Post post = createTestPost(member, "이메일 기반 좋아요 테스트");

        // when: 이메일로 좋아요 등록
        postLikeService.likePostByEmail(member.getEmail(), post.getId());

        // then: 이메일 기반 좋아요 여부 확인
        assertThat(
                postLikeService.hasLikedByEmail(member.getEmail(), post.getId())
        ).isTrue();

        // when: 이메일로 좋아요 취소
        postLikeService.unlikePostByEmail(member.getEmail(), post.getId());

        // then: 좋아요 취소 확인
        assertThat(
                postLikeService.hasLikedByEmail(member.getEmail(), post.getId())
        ).isFalse();
    }
}
