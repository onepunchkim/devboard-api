package com.example.devboard.service;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostImage;
import com.example.devboard.repository.PostImageRepository;
import com.example.devboard.repository.PostRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostImageServiceTest {

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ImageStorageService imageStorageService;

    private PostImageService postImageService;

    private Member member;
    private Post post;

    @BeforeEach
    void setUp() {
        // 실제 DB 연결 없이 Mock 객체를 주입한다.
        postImageService = new PostImageService(
                postImageRepository,
                postRepository,
                imageStorageService
        );

        member = new Member(
                "test@test.com",
                "encoded-password",
                "테스트회원"
        );

        post = new Post(
                "테스트 게시글",
                "테스트 내용",
                member
        );
    }

    // 테스트용 이미지 파일 생성
    private MockMultipartFile createFile(String filename) {
        return new MockMultipartFile(
                "files",
                filename,
                "image/png",
                new byte[]{1, 2, 3}
        );
    }

    @Test
    void uploadImages_shouldCleanupStoredFileWhenSecondUploadFails() {
        // given: 첫 번째 파일 저장 성공, 두 번째 파일 저장 실패
        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        when(postImageRepository.countByPostId(1L))
                .thenReturn(0L);

        when(imageStorageService.storeImage(any()))
                .thenReturn("uploads/post-images/first.png")
                .thenThrow(new IllegalStateException("두 번째 업로드 실패"));

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile first = createFile("first.png");
        MockMultipartFile second = createFile("second.png");

        // when: 두 번째 업로드 실패
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> postImageService.uploadImages(
                        1L,
                        List.of(first, second),
                        member.getEmail()
                )
        );

        // then: 원래 오류가 전달되고 첫 번째 파일이 정리되어야 한다.
        assertEquals("두 번째 업로드 실패", exception.getMessage());

        verify(imageStorageService).deleteImage(
                "uploads/post-images/first.png"
        );
    }

    @Test
    void uploadImages_shouldPreserveOriginalErrorWhenCleanupFails() {
        // given: 두 번째 업로드 실패 후 파일 정리도 실패
        when(postRepository.findById(1L))
                .thenReturn(Optional.of(post));

        when(postImageRepository.countByPostId(1L))
                .thenReturn(0L);

        when(imageStorageService.storeImage(any()))
                .thenReturn("uploads/post-images/first.png")
                .thenThrow(new IllegalStateException("원래 업로드 오류"));

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new IllegalStateException("파일 정리 오류"))
                .when(imageStorageService)
                .deleteImage("uploads/post-images/first.png");

        // when
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> postImageService.uploadImages(
                        1L,
                        List.of(
                                createFile("first.png"),
                                createFile("second.png")
                        ),
                        member.getEmail()
                )
        );

        // then: 정리 오류가 원래 업로드 오류를 덮어쓰면 안 된다.
        assertEquals("원래 업로드 오류", exception.getMessage());

        verify(imageStorageService).deleteImage(
                "uploads/post-images/first.png"
        );
    }
}
