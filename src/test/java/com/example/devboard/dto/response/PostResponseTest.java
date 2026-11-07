package com.example.devboard.dto.response;

import com.example.devboard.entity.Post;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// PostResponse DTO 변환 테스트
class PostResponseTest {

    @Test
    void from_shouldReturnNullMemberInfo_whenMemberIsNull() {
        // given: 작성자가 없는 게시글을 가정한다.
        Post post = mock(Post.class);

        when(post.getId()).thenReturn(1L);
        when(post.getTitle()).thenReturn("테스트 제목");
        when(post.getContent()).thenReturn("테스트 내용");
        when(post.getMember()).thenReturn(null);

        // when: 게시글 엔티티를 응답 DTO로 변환한다.
        PostResponse response = PostResponse.from(post);

        // then: 게시글 정보는 유지되고 작성자 정보는 null이어야 한다.
        assertEquals(1L, response.id());
        assertEquals("테스트 제목", response.title());
        assertEquals("테스트 내용", response.content());

        assertNull(response.memberId());
        assertNull(response.nickname());

        // 작성자 정보가 없는 경우에도 정상 변환되는지 확인한다.
        verify(post).getMember();
    }
}
