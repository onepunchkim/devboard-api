package com.example.devboard.controller;

import com.example.devboard.dto.response.PostResponse;
import com.example.devboard.entity.PostImage;
import com.example.devboard.service.PostImageService;
import com.example.devboard.service.PostLikeService;
import com.example.devboard.service.PostService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
// 컨트롤러 테스트에서는 보안 필터를 제외하고 요청을 테스트한다.
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 게시글 서비스 Mock 객체
    @MockitoBean
    private PostService postService;

    // 좋아요 서비스 Mock 객체
    @MockitoBean
    private PostLikeService postLikeService;

    // 이미지 서비스 Mock 객체
    @MockitoBean
    private PostImageService postImageService;

    // 테스트에서 사용할 인증 정보 생성
    private Authentication createAuthentication() {
        Authentication authentication = mock(Authentication.class);

        // 인증된 사용자의 이메일을 반환하도록 설정
        when(authentication.getName()).thenReturn("test@test.com");

        return authentication;
    }

    @Test
    void createPost_success() throws Exception {
        // given: 게시글 생성 응답 설정
        PostResponse response = mock(PostResponse.class);

        when(postService.createPost(
                eq("테스트 제목"),
                eq("테스트 내용"),
                eq("test@test.com")
        )).thenReturn(response);

        // when & then: 요청에 인증 객체를 직접 지정
        mockMvc.perform(post("/api/posts")
                        .principal(createAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "테스트 제목",
                                    "content": "테스트 내용"
                                }
                                """))
                .andExpect(status().isOk());

        // 서비스에 제목, 내용, 이메일이 전달됐는지 확인
        verify(postService).createPost(
                "테스트 제목",
                "테스트 내용",
                "test@test.com"
        );
    }

    @Test
    void findAll_success() throws Exception {
        // given: 게시글 목록 응답 설정
        Page<PostResponse> page = new PageImpl<>(
                List.of(mock(PostResponse.class)),
                PageRequest.of(0, 10),
                1
        );

        when(postService.findAll(null, 0, 10, "latest"))
                .thenReturn(page);

        // when & then: 기본 페이지 조회 요청
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));

        // 기본 검색 조건이 서비스에 전달됐는지 확인
        verify(postService).findAll(null, 0, 10, "latest");
    }

    @Test
    void findAll_withKeywordAndPaging_success() throws Exception {
        // given: 검색 결과가 없는 빈 페이지 설정
        Page<PostResponse> page = new PageImpl<>(
                List.of(),
                PageRequest.of(1, 5),
                0
        );

        when(postService.findAll("스프링", 1, 5, "oldest"))
                .thenReturn(page);

        // when & then: 검색어, 페이지, 크기, 정렬 기준 전달 확인
        mockMvc.perform(get("/api/posts")
                        .param("keyword", "스프링")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "oldest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        // 검색 조건이 서비스에 전달됐는지 확인
        verify(postService).findAll("스프링", 1, 5, "oldest");
    }

    @Test
    void findById_success() throws Exception {
        // given: 특정 게시글 응답 설정
        PostResponse response = mock(PostResponse.class);

        when(postService.findById(1L)).thenReturn(response);

        // when & then: 게시글 상세 조회 요청
        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk());

        // 게시글 ID가 서비스에 전달됐는지 확인
        verify(postService).findById(1L);
    }

    @Test
    void updatePost_success() throws Exception {
        // given: 게시글 수정 응답 설정
        PostResponse response = mock(PostResponse.class);

        when(postService.updatePost(
                eq(1L),
                eq("수정된 제목"),
                eq("수정된 내용"),
                eq("test@test.com")
        )).thenReturn(response);

        // when & then: 요청에 인증 객체를 직접 지정
        mockMvc.perform(put("/api/posts/1")
                        .principal(createAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "수정된 제목",
                                    "content": "수정된 내용"
                                }
                                """))
                .andExpect(status().isOk());

        // 게시글 ID, 수정 내용, 작성자 이메일 전달 확인
        verify(postService).updatePost(
                1L,
                "수정된 제목",
                "수정된 내용",
                "test@test.com"
        );
    }

    @Test
    void deletePost_success() throws Exception {
        // when & then: 요청에 인증 객체를 직접 지정
        mockMvc.perform(delete("/api/posts/1")
                        .principal(createAuthentication()))
                .andExpect(status().isNoContent());

        // 게시글 ID와 작성자 이메일 전달 확인
        verify(postService).deletePost(1L, "test@test.com");
    }

    @Test
    void getLikeCounts_success() throws Exception {
        // given: 게시글 1번은 좋아요 3개, 2번은 결과에 없는 상황
        when(postLikeService.getLikeCounts(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, 3L));

        // when & then: 누락된 게시글의 좋아요 수는 0인지 확인
        mockMvc.perform(get("/api/posts/counts")
                        .param("postIds", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1']").value(3))
                .andExpect(jsonPath("$['2']").value(0));

        // 게시글 ID 목록이 서비스에 전달됐는지 확인
        verify(postLikeService).getLikeCounts(List.of(1L, 2L));
    }

    @Test
    void getPostImages_success() throws Exception {
        // given: 이미지가 없는 게시글 설정
        when(postImageService.findImagesByPostId(1L))
                .thenReturn(List.of());

        // when & then: 이미지 목록이 빈 배열인지 확인
        mockMvc.perform(get("/api/posts/1/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        // 게시글 ID가 이미지 서비스에 전달됐는지 확인
        verify(postImageService).findImagesByPostId(1L);
    }

    @Test
    void uploadImages_success() throws Exception {
        // given: 테스트용 이미지 파일 생성
        MockMultipartFile file = new MockMultipartFile(
                "files",
                "test.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );

        // 이미지 응답에 사용할 Mock 객체 생성
        PostImage image = mock(PostImage.class);

        when(image.getId()).thenReturn(1L);
        when(image.getImagePath())
                .thenReturn("uploads/post-images/test.png");
        when(image.getOriginalFilename()).thenReturn("test.png");

        when(postImageService.uploadImages(
                eq(1L),
                anyList(),
                eq("test@test.com")
        )).thenReturn(List.of(image));

        // when & then: 요청에 인증 객체를 직접 지정
        mockMvc.perform(multipart("/api/posts/1/images")
                        .file(file)
                        .principal(createAuthentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].originalFilename").value("test.png"));

        // 게시글 ID, 파일 목록, 작성자 이메일 전달 확인
        verify(postImageService).uploadImages(
                eq(1L),
                anyList(),
                eq("test@test.com")
        );
    }

    @Test
    void deletePostImage_success() throws Exception {
        // when & then: 요청에 인증 객체를 직접 지정
        mockMvc.perform(delete("/api/posts/1/images/2")
                        .principal(createAuthentication()))
                .andExpect(status().isNoContent());

        // 게시글 ID, 이미지 ID, 작성자 이메일 전달 확인
        verify(postImageService).deleteImage(
                1L,
                2L,
                "test@test.com"
        );
    }


    // ==========================================
    // 1. 검색 결과가 없는 경우
    // ==========================================
    @Test
    void findAll_shouldReturnEmptyPage() throws Exception {

        // given: 조회 결과가 없는 빈 페이지
        Page<PostResponse> emptyPage = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0
        );

        when(postService.findAll(null, 0, 10, "latest"))
                .thenReturn(emptyPage);

        // when & then: 빈 게시글 목록 반환
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(postService).findAll(null, 0, 10, "latest");
    }

    // ==========================================
    // 2. 이미지가 없는 게시글의 목록 조회
    // ==========================================
    @Test
    void getPostImages_shouldReturnEmptyList() throws Exception {

        // given: 이미지가 등록되지 않은 게시글
        when(postImageService.findImagesByPostId(10L))
                .thenReturn(List.of());

        // when & then: 빈 배열 반환
        mockMvc.perform(get("/api/posts/10/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(postImageService).findImagesByPostId(10L);
    }

    // ==========================================
    // 3. 좋아요가 없는 게시글의 좋아요 수 조회
    // ==========================================
    @Test
    void getLikeCounts_shouldReturnZeroForPostsWithoutLikes()
            throws Exception {

        // given: 두 게시글 모두 좋아요가 없음
        when(postLikeService.getLikeCounts(List.of(10L, 20L)))
                .thenReturn(Map.of());

        // when & then: 각 게시글의 좋아요 수가 0인지 확인
        mockMvc.perform(get("/api/posts/counts")
                        .param("postIds", "10", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['10']").value(0))
                .andExpect(jsonPath("$['20']").value(0));

        verify(postLikeService).getLikeCounts(List.of(10L, 20L));
    }

    //=============================================================================================

    // ==========================================
    // 1. 게시글 작성: 제목이 비어 있는 경우
    // ==========================================
    @Test
    void createPost_shouldRejectBlankTitle() throws Exception {

        // 제목이 공백이면 요청 검증에서 차단되어야 한다.
        mockMvc.perform(post("/api/posts")
                        .principal(createAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "   ",
                                    "content": "테스트 내용"
                                }
                                """))
                .andExpect(status().isBadRequest());

        // 잘못된 요청은 서비스에 전달되지 않아야 한다.
        org.mockito.Mockito.verifyNoInteractions(postService);
    }

    // ==========================================
    // 2. 게시글 작성: 내용이 비어 있는 경우
    // ==========================================
    @Test
    void createPost_shouldRejectBlankContent() throws Exception {

        mockMvc.perform(post("/api/posts")
                        .principal(createAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "정상 제목",
                                    "content": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(postService);
    }

    // ==========================================
    // 3. 게시글 작성: 제목이 200자를 초과하는 경우
    // ==========================================
    @Test
    void createPost_shouldRejectTooLongTitle() throws Exception {

        // 201자 제목 생성
        String longTitle = "A".repeat(201);

        String requestBody = """
                {
                    "title": "%s",
                    "content": "테스트 내용"
                }
                """.formatted(longTitle);

        mockMvc.perform(post("/api/posts")
                        .principal(createAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(postService);
    }

    // ==========================================
    // 4. 게시글 수정: 제목이 없는 경우
    // ==========================================
    @Test
    void updatePost_shouldRejectMissingTitle() throws Exception {

        mockMvc.perform(put("/api/posts/1")
                        .principal(createAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "content": "수정할 내용"
                                }
                                """))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(postService);
    }

    // ==========================================
    // 5. 게시글 작성: 잘못된 JSON 요청
    // ==========================================
    @Test
    void createPost_shouldRejectInvalidJson() throws Exception {

        // JSON 문법이 올바르지 않은 요청
        mockMvc.perform(post("/api/posts")
                        .principal(createAuthentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "잘못된 JSON",
                                    "content":
                                }
                                """))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(postService);
    }

    // ==========================================
    // 6. 게시글 ID가 숫자가 아닌 경우
    // ==========================================
    @Test
    void findById_shouldRejectInvalidId() throws Exception {

        // given: Long으로 변환할 수 없는 게시글 ID
        String invalidId = "abc";

        // when & then: 잘못된 요청은 HTTP 400 반환
        mockMvc.perform(get("/api/posts/{id}", invalidId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("요청 파라미터의 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.timestamp").exists());

        // 타입 변환에 실패했으므로 서비스는 호출되지 않아야 함
        org.mockito.Mockito.verifyNoInteractions(postService);
    }


    // ==========================================
    // 7. 좋아요 수 조회: 중복 게시글 ID 제거
    // ==========================================
    @Test
    void getLikeCounts_shouldRemoveDuplicatePostIds()
            throws Exception {

        // 서비스가 반환하는 좋아요 수
        when(postLikeService.getLikeCounts(
                List.of(1L, 1L, 2L)
        )).thenReturn(Map.of(
                1L, 3L,
                2L, 5L
        ));

        // 같은 게시글 ID를 중복 전달
        mockMvc.perform(get("/api/posts/counts")
                        .param("postIds", "1", "1", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1']").value(3))
                .andExpect(jsonPath("$['2']").value(5));

        // 현재 Controller는 중복된 입력 목록을 서비스에 전달한다.
        verify(postLikeService).getLikeCounts(
                List.of(1L, 1L, 2L)
        );
    }

}