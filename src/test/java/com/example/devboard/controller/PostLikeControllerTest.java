package com.example.devboard.controller;

import com.example.devboard.exception.BusinessException;
import com.example.devboard.exception.ErrorCode;
import com.example.devboard.service.PostLikeService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

    // 테스트용 인증 객체
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                    "test@test.com",
                    null,
                    List.of()
            );

    // =====================================================
    // 1. 좋아요 등록 성공
    // =====================================================

    @Test
    void likePost_success() throws Exception {

        // when & then: 좋아요 등록 요청 및 응답 검증
        mockMvc.perform(
                        post("/api/posts/{postId}/like", 1L)
                                .principal(authentication)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.message")
                        .value("좋아요를 등록했습니다."));

        // 로그인 이메일과 게시글 ID가 정확하게 전달되는지 확인
        verify(postLikeService).likePostByEmail(
                "test@test.com",
                1L
        );
    }

    // =====================================================
    // 2. 좋아요 취소 성공
    // =====================================================

    @Test
    void unlikePost_success() throws Exception {

        // when & then: 좋아요 취소 성공 시 HTTP 200 반환
        mockMvc.perform(
                        delete("/api/posts/{postId}/like", 1L)
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.message")
                        .value("좋아요를 취소했습니다."));

        verify(postLikeService).unlikePostByEmail(
                "test@test.com",
                1L
        );
    }

    // =====================================================
    // 3. 좋아요 정보 조회 - 좋아요 등록 회원
    // =====================================================

    @Test
    void getLikeInfo_liked_success() throws Exception {

        // given: 전체 좋아요 5개, 현재 회원은 좋아요 등록 상태
        when(postLikeService.getLikeCount(1L))
                .thenReturn(5L);

        when(postLikeService.hasLikedByEmail(
                "test@test.com", 1L
        )).thenReturn(true);

        // when & then
        mockMvc.perform(
                        get("/api/posts/{postId}/like", 1L)
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.likeCount").value(5))
                .andExpect(jsonPath("$.liked").value(true));

        verify(postLikeService).getLikeCount(1L);

        verify(postLikeService).hasLikedByEmail(
                "test@test.com",
                1L
        );
    }

    // =====================================================
    // 4. 좋아요 정보 조회 - 좋아요 미등록 회원
    // =====================================================

    @Test
    void getLikeInfo_notLiked_success() throws Exception {

        // given
        when(postLikeService.getLikeCount(1L))
                .thenReturn(3L);

        when(postLikeService.hasLikedByEmail(
                "test@test.com", 1L
        )).thenReturn(false);

        // when & then
        mockMvc.perform(
                        get("/api/posts/{postId}/like", 1L)
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.liked").value(false));
    }

    // =====================================================
    // 5. 좋아요가 없는 게시글 조회
    // =====================================================

    @Test
    void getLikeInfo_zeroCount_success() throws Exception {

        // given
        when(postLikeService.getLikeCount(1L))
                .thenReturn(0L);

        when(postLikeService.hasLikedByEmail(
                "test@test.com", 1L
        )).thenReturn(false);

        // when & then
        mockMvc.perform(
                        get("/api/posts/{postId}/like", 1L)
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    // =====================================================
    // 6. 존재하지 않는 게시글에 좋아요 등록
    // =====================================================

    @Test
    void likePost_shouldReturn404WhenPostNotFound()
            throws Exception {

        // given: 실제 Service와 동일한 비즈니스 예외 발생
        doThrow(new BusinessException(
                ErrorCode.POST_NOT_FOUND
        ))
                .when(postLikeService)
                .likePostByEmail("test@test.com", 999L);

        // when & then: HTTP 404 및 오류 응답 검증
        mockMvc.perform(
                        post("/api/posts/{postId}/like", 999L)
                                .principal(authentication)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.POST_NOT_FOUND.getMessage()));

        verify(postLikeService).likePostByEmail(
                "test@test.com",
                999L
        );
    }

    // =====================================================
    // 7. 중복 좋아요 등록 방지
    // =====================================================

    @Test
    void likePost_shouldReturn409WhenAlreadyLiked()
            throws Exception {

        // given: 이미 좋아요가 등록된 상태
        doThrow(new BusinessException(
                ErrorCode.LIKE_ALREADY_EXISTS
        ))
                .when(postLikeService)
                .likePostByEmail("test@test.com", 1L);

        // when & then: HTTP 409 Conflict 검증
        mockMvc.perform(
                        post("/api/posts/{postId}/like", 1L)
                                .principal(authentication)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.LIKE_ALREADY_EXISTS.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.LIKE_ALREADY_EXISTS.getMessage()));

        verify(postLikeService).likePostByEmail(
                "test@test.com",
                1L
        );
    }

    // =====================================================
    // 8. 잘못된 게시글 ID 형식
    // =====================================================

    @Test
    void likePost_shouldRejectInvalidPostId()
            throws Exception {

        // given: Long 타입으로 변환할 수 없는 게시글 ID
        String invalidPostId = "abc";

        // when & then
        mockMvc.perform(
                        post("/api/posts/{postId}/like", invalidPostId)
                                .principal(authentication)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 파라미터의 형식이 올바르지 않습니다."));

        // 잘못된 요청이므로 Service는 호출되지 않아야 함
        verifyNoInteractions(postLikeService);
    }

    // =====================================================
    // 9. 존재하지 않는 좋아요 취소
    // =====================================================

    @Test
    void unlikePost_shouldReturn404WhenLikeNotFound()
            throws Exception {

        // given: 좋아요 기록이 존재하지 않는 상태
        doThrow(new BusinessException(
                ErrorCode.LIKE_NOT_FOUND
        ))
                .when(postLikeService)
                .unlikePostByEmail("test@test.com", 1L);

        // when & then: HTTP 404 및 오류 응답 검증
        mockMvc.perform(
                        delete("/api/posts/{postId}/like", 1L)
                                .principal(authentication)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code")
                        .value(ErrorCode.LIKE_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.LIKE_NOT_FOUND.getMessage()));

        verify(postLikeService).unlikePostByEmail(
                "test@test.com",
                1L
        );
    }
}
