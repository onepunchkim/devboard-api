package com.example.devboard.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostLikeTest {

    @Test
    void createPostLike_success() {
        // given: 좋아요를 등록할 회원과 게시글
        Member member = mock(Member.class);
        Post post = mock(Post.class);

        // when: 좋아요 엔티티 생성
        PostLike postLike = new PostLike(member, post);

        // then: 전달한 회원과 게시글이 저장되어야 한다.
        assertSame(member, postLike.getMember());
        assertSame(post, postLike.getPost());

        // DB에 저장하기 전에는 ID와 생성 시간이 없다.
        assertNull(postLike.getId());
        assertNull(postLike.getCreatedAt());
    }

    @Test
    void onCreate_shouldSetCreatedAt() {
        // given: 생성 시간이 아직 설정되지 않은 좋아요
        PostLike postLike = new PostLike(
                mock(Member.class),
                mock(Post.class)
        );

        LocalDateTime before = LocalDateTime.now();

        // when: JPA 저장 직전 호출되는 메서드 실행
        postLike.onCreate();

        LocalDateTime after = LocalDateTime.now();

        // then: 생성 시간이 현재 시각 범위에 있어야 한다.
        assertNotNull(postLike.getCreatedAt());
        assertFalse(postLike.getCreatedAt().isBefore(before));
        assertFalse(postLike.getCreatedAt().isAfter(after));
    }

    @Test
    void defaultConstructor_shouldInitializeFieldsAsNull() {
        // given & when: JPA에서 사용하는 기본 생성자 호출
        PostLike postLike = new PostLike();

        // then: 저장 전에는 필드가 초기화되지 않은 상태
        assertNull(postLike.getId());
        assertNull(postLike.getMember());
        assertNull(postLike.getPost());
        assertNull(postLike.getCreatedAt());
    }
}
