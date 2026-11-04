package com.example.devboard.controller;

import com.example.devboard.service.PostLikeService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostLikeController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostLikeService postLikeService;

    // 테스트용 로그인 사용자
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                    "test@test.com",
                    null,
                    List.of()
            );

    // 1. 좋아요 등록 성공
    @Test
    void likePost_success() throws Exception {

        // when & then: 좋아요 등록 시 HTTP 201 반환
        mockMvc.perform(post("/api/posts/{postId}/like", 1L)
                        .principal(authentication))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.message")
                        .value("좋아요를 등록했습니다."));

        // 로그인 이메일과 게시글 ID 전달 확인
        verify(postLikeService).likePostByEmail(
                "test@test.com", 1L
        );
    }

    // 2. 좋아요 취소 성공
    @Test
    void unlikePost_success() throws Exception {

        // when & then: 좋아요 취소 시 HTTP 200 반환
        mockMvc.perform(delete("/api/posts/{postId}/like", 1L)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.message")
                        .value("좋아요를 취소했습니다."));

        verify(postLikeService).unlikePostByEmail(
                "test@test.com", 1L
        );
    }

    // 3. 좋아요 정보 조회: 좋아요를 누른 사용자
    @Test
    void getLikeInfo_liked_success() throws Exception {

        // given: 좋아요 수 5개, 현재 사용자 좋아요 등록
        when(postLikeService.getLikeCount(1L))
                .thenReturn(5L);

        when(postLikeService.hasLikedByEmail(
                "test@test.com", 1L
        )).thenReturn(true);

        // when & then: 좋아요 수와 사용자 상태 확인
        mockMvc.perform(get("/api/posts/{postId}/like", 1L)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.likeCount").value(5))
                .andExpect(jsonPath("$.liked").value(true));

        verify(postLikeService).getLikeCount(1L);
        verify(postLikeService).hasLikedByEmail(
                "test@test.com", 1L
        );
    }

    // 4. 좋아요 정보 조회: 좋아요를 누르지 않은 사용자
    @Test
    void getLikeInfo_notLiked_success() throws Exception {

        // given: 좋아요 수 3개, 현재 사용자는 좋아요 미등록
        when(postLikeService.getLikeCount(1L))
                .thenReturn(3L);

        when(postLikeService.hasLikedByEmail(
                "test@test.com", 1L
        )).thenReturn(false);

        // when & then
        mockMvc.perform(get("/api/posts/{postId}/like", 1L)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.liked").value(false));
    }

    // 5. 좋아요가 0개인 게시글 조회
    @Test
    void getLikeInfo_zeroCount_success() throws Exception {

        // given: 좋아요가 없는 게시글
        when(postLikeService.getLikeCount(1L))
                .thenReturn(0L);

        when(postLikeService.hasLikedByEmail(
                "test@test.com", 1L
        )).thenReturn(false);

        // when & then: 좋아요 수 0 확인
        mockMvc.perform(get("/api/posts/{postId}/like", 1L)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    // 6. 좋아요 등록 시 존재하지 않는 게시글
    @Test
    void likePost_shouldReturn404WhenPostNotFound() throws Exception {

        // given: 서비스에서 게시글 없음 예외 발생
        doThrow(new EntityNotFoundException("게시글을 찾을 수 없습니다."))
                .when(postLikeService)
                .likePostByEmail("test@test.com", 999L);

        // when & then: HTTP 404 반환
        mockMvc.perform(post("/api/posts/{postId}/like", 999L)
                        .principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("게시글을 찾을 수 없습니다."));

        verify(postLikeService).likePostByEmail(
                "test@test.com", 999L
        );
    }

    // 7. 좋아요 중복 등록 시 충돌 응답
    @Test
    void likePost_shouldReturn409WhenAlreadyLiked() throws Exception {

        // given: 서비스에서 중복 좋아요 예외 발생
        doThrow(new IllegalStateException("이미 좋아요를 누른 게시글입니다."))
                .when(postLikeService)
                .likePostByEmail("test@test.com", 1L);

        // when & then: HTTP 409 반환
        mockMvc.perform(post("/api/posts/{postId}/like", 1L)
                        .principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("이미 좋아요를 누른 게시글입니다."));
    }

    // 8. 좋아요 등록 시 잘못된 게시글 ID
    @Test
    void likePost_shouldRejectInvalidPostId() throws Exception {

        // given: 숫자로 변환할 수 없는 게시글 ID
        String invalidPostId = "abc";

        // when & then: HTTP 400 반환
        mockMvc.perform(post("/api/posts/{postId}/like", invalidPostId)
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 파라미터의 형식이 올바르지 않습니다."));

        // 컨트롤러에 진입하지 못했으므로 서비스 호출 없음
        verifyNoInteractions(postLikeService);
    }

    // 9. 좋아요 취소 시 존재하지 않는 좋아요
    @Test
    void unlikePost_shouldReturn404WhenLikeNotFound() throws Exception {

        // given: 취소할 좋아요가 존재하지 않음
        doThrow(new EntityNotFoundException("좋아요를 찾을 수 없습니다."))
                .when(postLikeService)
                .unlikePostByEmail("test@test.com", 1L);

        // when & then: HTTP 404 반환
        mockMvc.perform(delete("/api/posts/{postId}/like", 1L)
                        .principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("좋아요를 찾을 수 없습니다."));
    }
}
