package com.example.devboard.controller;

import com.example.devboard.dto.request.CommentCreateRequest;
import com.example.devboard.dto.response.CommentResponse;
import com.example.devboard.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    // 테스트용 로그인 사용자
    private final UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                    "test@test.com",
                    null,
                    List.of()
            );

    // 1. 댓글 작성 성공
    @Test
    void createComment_success() throws Exception {

        // given: 댓글 작성 요청
        String requestJson = """
            {
                "content": "테스트 댓글입니다."
            }
            """;

        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 4, 10, 0);

        // 댓글 작성 후 반환될 응답 DTO
        CommentResponse response = new CommentResponse(
                10L,
                "테스트 댓글입니다.",
                1L,
                2L,
                "테스트유저",
                createdAt,
                createdAt
        );

        // 서비스 반환값 설정
        when(commentService.createComment(
                eq(1L),
                eq("test@test.com"),
                any(CommentCreateRequest.class)
        )).thenReturn(response);

        // when & then: HTTP 201 및 응답 JSON 검증
        mockMvc.perform(post("/api/posts/{postId}/comments", 1L)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.content").value("테스트 댓글입니다."))
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.memberId").value(2))
                .andExpect(jsonPath("$.nickname").value("테스트유저"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-04T10:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-04T10:00:00"));

        // 서비스에 전달된 사용자 및 댓글 내용 확인
        verify(commentService).createComment(
                eq(1L),
                eq("test@test.com"),
                argThat(request ->
                        "테스트 댓글입니다.".equals(request.content()))
        );
    }

    // 2. 댓글 목록 조회 성공
    @Test
    void findComments_success() throws Exception {

        // given: 댓글 2개
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 4, 10, 0);

        CommentResponse firstComment = new CommentResponse(
                10L,
                "첫 번째 댓글",
                1L,
                2L,
                "사용자A",
                createdAt,
                createdAt
        );

        CommentResponse secondComment = new CommentResponse(
                11L,
                "두 번째 댓글",
                1L,
                3L,
                "사용자B",
                createdAt.plusMinutes(5),
                createdAt.plusMinutes(5)
        );

        when(commentService.findComments(1L))
                .thenReturn(List.of(firstComment, secondComment));

        // when & then: 반환된 댓글 목록 확인
        mockMvc.perform(get("/api/posts/{postId}/comments", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].content").value("첫 번째 댓글"))
                .andExpect(jsonPath("$[0].nickname").value("사용자A"))
                .andExpect(jsonPath("$[1].id").value(11))
                .andExpect(jsonPath("$[1].content").value("두 번째 댓글"))
                .andExpect(jsonPath("$[1].nickname").value("사용자B"));

        verify(commentService).findComments(1L);
    }

    // 3. 댓글 수정 성공
    @Test
    void updateComment_success() throws Exception {

        // given: 댓글 수정 요청
        String requestJson = """
            {
                "content": "수정된 댓글입니다."
            }
            """;

        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 4, 10, 0);
        LocalDateTime updatedAt = createdAt.plusMinutes(30);

        CommentResponse response = new CommentResponse(
                10L,
                "수정된 댓글입니다.",
                1L,
                2L,
                "테스트유저",
                createdAt,
                updatedAt
        );

        when(commentService.updateComment(
                eq(10L),
                eq("test@test.com"),
                any(CommentCreateRequest.class)
        )).thenReturn(response);

        // when & then: HTTP 200 및 수정된 댓글 정보 확인
        mockMvc.perform(put("/api/comments/{commentId}", 10L)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.content").value("수정된 댓글입니다."))
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.memberId").value(2))
                .andExpect(jsonPath("$.nickname").value("테스트유저"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-04T10:00:00"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-04T10:30:00"));

        // 서비스에 전달된 댓글 ID, 사용자, 수정 내용 확인
        verify(commentService).updateComment(
                eq(10L),
                eq("test@test.com"),
                argThat(request ->
                        "수정된 댓글입니다.".equals(request.content()))
        );
    }

    // 4. 댓글 삭제 성공
    @Test
    void deleteComment_success() throws Exception {

        // when & then: 댓글 삭제 시 HTTP 204 반환
        mockMvc.perform(delete("/api/comments/{commentId}", 1L)
                        .principal(authentication))
                .andExpect(status().isNoContent());

        verify(commentService).deleteComment(
                1L,
                "test@test.com"
        );
    }

    // 5. 댓글 목록 조회 시 잘못된 게시글 ID
    @Test
    void findComments_shouldRejectInvalidPostId() throws Exception {

        // given: Long으로 변환할 수 없는 ID
        String invalidPostId = "abc";

        // when & then: HTTP 400 반환
        mockMvc.perform(get("/api/posts/{postId}/comments", invalidPostId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 파라미터의 형식이 올바르지 않습니다."));

        // 타입 변환에 실패했으므로 서비스는 실행되지 않음
        verifyNoInteractions(commentService);
    }

    // 6. 댓글 작성 시 빈 내용 검증
    @Test
    void createComment_shouldRejectBlankContent() throws Exception {

        // given: 공백만 입력한 댓글
        String requestJson = """
            {
                "content": "   "
            }
            """;

        // when & then: HTTP 400 및 검증 메시지 확인
        mockMvc.perform(post("/api/posts/{postId}/comments", 1L)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.content")
                        .value("댓글 내용을 입력해 주세요."));

        // 검증 실패 시 서비스 호출 방지
        verifyNoInteractions(commentService);
    }


    // 7. 댓글 작성 시 1000자 초과 검증
    @Test
    void createComment_shouldRejectTooLongContent() throws Exception {

        // given: 최대 길이를 초과한 댓글
        String longContent = "가".repeat(1001);

        String requestJson = """
            {
                "content": "%s"
            }
            """.formatted(longContent);

        // when & then: HTTP 400 및 길이 제한 검증
        mockMvc.perform(post("/api/posts/{postId}/comments", 1L)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.content")
                        .value("댓글은 1000자 이하로 작성해 주세요."));

        verifyNoInteractions(commentService);
    }


    // 8. 댓글 수정 시 내용 누락 검증
    @Test
    void updateComment_shouldRejectMissingContent() throws Exception {

        // given: content 필드가 없는 요청
        String requestJson = "{}";

        // when & then: HTTP 400 반환
        mockMvc.perform(put("/api/comments/{commentId}", 1L)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.content")
                        .value("댓글 내용을 입력해 주세요."));

        verifyNoInteractions(commentService);
    }


    // 9. 댓글 작성 시 잘못된 JSON 검증
    @Test
    void createComment_shouldRejectInvalidJson() throws Exception {

        // given: 문법이 잘못된 JSON
        String invalidJson = """
            {
                "content":
            }
            """;

        // when & then: HTTP 400 반환
        mockMvc.perform(post("/api/posts/{postId}/comments", 1L)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 본문이 올바른 JSON 형식이 아닙니다."));

        verifyNoInteractions(commentService);
    }
}