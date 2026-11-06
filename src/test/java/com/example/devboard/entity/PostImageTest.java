package com.example.devboard.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PostImageTest {

    @Test
    void createPostImage_success() {
        // given: 이미지 경로, 원본 파일명, 게시글
        String imagePath = "/uploads/posts/test-image.png";
        String originalFilename = "test-image.png";
        Post post = mock(Post.class);

        // when: 이미지 엔티티 생성
        PostImage postImage = new PostImage(
                imagePath,
                originalFilename,
                post
        );

        // then: 생성자에서 전달한 값이 정확히 저장되는지 확인
        assertEquals(imagePath, postImage.getImagePath());
        assertEquals(originalFilename, postImage.getOriginalFilename());
        assertSame(post, postImage.getPost());

        // DB 저장 전에는 ID와 생성 시간이 없다.
        assertNull(postImage.getId());
        assertNull(postImage.getCreatedAt());
    }

    @Test
    void onCreate_shouldSetCreatedAt() {
        // given: 아직 저장하지 않은 이미지 엔티티
        PostImage postImage = new PostImage(
                "/uploads/posts/test-image.png",
                "test-image.png",
                mock(Post.class)
        );

        LocalDateTime before = LocalDateTime.now();

        // when: JPA @PrePersist 콜백을 직접 실행
        postImage.onCreate();

        LocalDateTime after = LocalDateTime.now();

        // then: 생성 시간이 정상적으로 설정되는지 확인
        assertNotNull(postImage.getCreatedAt());
        assertFalse(postImage.getCreatedAt().isBefore(before));
        assertFalse(postImage.getCreatedAt().isAfter(after));
    }

    @Test
    void defaultConstructor_shouldInitializeFieldsAsNull() {
        // given & when: JPA 기본 생성자 호출
        PostImage postImage = new PostImage();

        // then: 생성 직후 모든 필드의 초기 상태 확인
        assertNull(postImage.getId());
        assertNull(postImage.getImagePath());
        assertNull(postImage.getOriginalFilename());
        assertNull(postImage.getCreatedAt());
        assertNull(postImage.getPost());
    }
}
