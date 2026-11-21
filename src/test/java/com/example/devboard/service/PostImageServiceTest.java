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

import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
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

    private static final Long POST_ID = 1L;
    private static final Long IMAGE_ID = 10L;

    private static final String EMAIL = "test@test.com";
    private static final String OTHER_EMAIL = "other@test.com";

    private static final String IMAGE_PATH =
            "uploads/post-images/photo.png";

    // =====================================================
    // 테스트 환경 설정
    // =====================================================

    @BeforeEach
    void setUp() {

        // 실제 DB나 파일 저장소 대신 Mock 객체 사용
        postImageService = new PostImageService(
                postImageRepository,
                postRepository,
                imageStorageService
        );

        // 테스트용 회원
        member = new Member(
                EMAIL,
                "encoded-password",
                "테스트회원"
        );

        // 테스트용 게시글
        post = new Post(
                "테스트 게시글",
                "테스트 내용",
                member
        );
    }

    // =====================================================
    // 테스트용 이미지 파일 생성
    // =====================================================

    private MockMultipartFile createFile(String filename) {

        return new MockMultipartFile(
                "files",
                filename,
                "image/png",
                new byte[]{1, 2, 3}
        );
    }

    // =====================================================
    // 테스트용 게시글 조회 설정
    // =====================================================

    private void mockExistingPost() {

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(post));
    }

    // =====================================================
    // 테스트용 게시글 이미지 생성
    // =====================================================

    private PostImage createPostImage() {

        return new PostImage(
                IMAGE_PATH,
                "photo.png",
                post
        );
    }

    // =====================================================
    // 1. 이미지 목록이 null이면 400
    // =====================================================

    @Test
    void uploadImages_shouldRejectNullFileList() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        null,
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        // 파일 검증 단계에서 거부되므로 외부 의존성 호출 없음
        verifyNoInteractions(
                postRepository,
                postImageRepository,
                imageStorageService
        );
    }

    // =====================================================
    // 2. 빈 이미지 목록이면 400
    // =====================================================

    @Test
    void uploadImages_shouldRejectEmptyFileList() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(),
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 3. null 이미지 파일이 포함되면 400
    // =====================================================

    @Test
    void uploadImages_shouldRejectNullFile() {

        // List.of()는 null을 허용하지 않으므로
        // Arrays.asList() 사용
        List<MultipartFile> files = Arrays.asList(
                createFile("first.png"),
                null
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        files,
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 4. 빈 이미지 파일이 포함되면 400
    // =====================================================

    @Test
    void uploadImages_shouldRejectEmptyFile() {

        MockMultipartFile emptyFile = new MockMultipartFile(
                "files",
                "empty.png",
                "image/png",
                new byte[0]
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(emptyFile),
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 5. 존재하지 않는 게시글에 업로드하면 404
    // =====================================================

    @Test
    void uploadImages_shouldReturn404WhenPostMissing() {

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(createFile("photo.png")),
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 6. 게시글 작성자가 아니면 업로드 거부
    // =====================================================

    @Test
    void uploadImages_shouldRejectNonAuthor() {

        mockExistingPost();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(createFile("photo.png")),
                        OTHER_EMAIL
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 7. 게시글 작성자 정보가 없으면 업로드 거부
    // =====================================================

    @Test
    void uploadImages_shouldRejectPostWithoutMember() {

        // 회원 정보가 없는 게시글을 Mock으로 생성
        Post postWithoutMember = mock(Post.class);

        when(postWithoutMember.getMember())
                .thenReturn(null);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(postWithoutMember));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(createFile("photo.png")),
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 8. 게시글 이미지가 5장을 초과하면 400
    // =====================================================

    @Test
    void uploadImages_shouldRejectMoreThanFiveImages() {

        mockExistingPost();

        // 기존 이미지 4장
        when(postImageRepository.countByPostId(POST_ID))
                .thenReturn(4L);

        // 이미지 2장 추가 요청 → 총 6장
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(
                                createFile("first.png"),
                                createFile("second.png")
                        ),
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 9. 이미지 5장까지는 업로드 허용
    // =====================================================

    @Test
    void uploadImages_shouldAllowExactlyFiveImages() {

        mockExistingPost();

        // 기존 이미지 4장
        when(postImageRepository.countByPostId(POST_ID))
                .thenReturn(4L);

        when(imageStorageService.storeImage(any()))
                .thenReturn(IMAGE_PATH);

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 이미지 1장 추가 → 총 5장
        List<PostImage> result = postImageService.uploadImages(
                POST_ID,
                List.of(createFile("photo.png")),
                EMAIL
        );

        assertEquals(1, result.size());

        verify(imageStorageService).storeImage(any());
        verify(postImageRepository).save(any(PostImage.class));
    }

    // =====================================================
    // 10. 원본 파일명이 null이면 기본 이름 사용
    // =====================================================

    @Test
    void uploadImages_shouldUseDefaultNameWhenFilenameNull() {

        mockExistingPost();

        MockMultipartFile file = createFile(null);

        when(imageStorageService.storeImage(any()))
                .thenReturn(IMAGE_PATH);

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        postImageService.uploadImages(
                POST_ID,
                List.of(file),
                EMAIL
        );

        // DB 저장 시 생성된 PostImage 객체 확인
        org.mockito.ArgumentCaptor<PostImage> captor =
                org.mockito.ArgumentCaptor.forClass(PostImage.class);

        verify(postImageRepository).save(captor.capture());

        assertEquals(
                "image",
                captor.getValue().getOriginalFilename()
        );
    }

    // =====================================================
    // 11. 원본 파일명이 공백이면 기본 이름 사용
    // =====================================================

    @Test
    void uploadImages_shouldUseDefaultNameWhenFilenameBlank() {

        mockExistingPost();

        MockMultipartFile file = createFile("   ");

        when(imageStorageService.storeImage(any()))
                .thenReturn(IMAGE_PATH);

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        postImageService.uploadImages(
                POST_ID,
                List.of(file),
                EMAIL
        );

        org.mockito.ArgumentCaptor<PostImage> captor =
                org.mockito.ArgumentCaptor.forClass(PostImage.class);

        verify(postImageRepository).save(captor.capture());

        assertEquals(
                "image",
                captor.getValue().getOriginalFilename()
        );
    }

    // =====================================================
    // 12. 정상 이미지 업로드
    // =====================================================

    @Test
    void uploadImages_shouldSaveImagesSuccessfully() {

        mockExistingPost();

        when(imageStorageService.storeImage(any()))
                .thenReturn(IMAGE_PATH);

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<PostImage> result = postImageService.uploadImages(
                POST_ID,
                List.of(createFile("photo.png")),
                EMAIL
        );

        assertEquals(1, result.size());

        assertEquals(
                IMAGE_PATH,
                result.get(0).getImagePath()
        );

        assertEquals(
                "photo.png",
                result.get(0).getOriginalFilename()
        );

        verify(imageStorageService).storeImage(any());
        verify(postImageRepository).save(any(PostImage.class));
    }

    // =====================================================
    // 13. 두 번째 업로드 실패 시 첫 번째 파일 정리
    // 기존 테스트 유지
    // =====================================================

    @Test
    void uploadImages_shouldCleanupStoredFileWhenSecondUploadFails() {

        mockExistingPost();

        when(imageStorageService.storeImage(any()))
                .thenReturn("uploads/post-images/first.png")
                .thenThrow(
                        new IllegalStateException("두 번째 업로드 실패")
                );

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(
                                createFile("first.png"),
                                createFile("second.png")
                        ),
                        EMAIL
                )
        );

        assertEquals(
                "두 번째 업로드 실패",
                exception.getMessage()
        );

        verify(imageStorageService).deleteImage(
                "uploads/post-images/first.png"
        );
    }

    // =====================================================
    // 14. 파일 정리 실패 시 원래 업로드 오류 유지
    // 기존 테스트 유지
    // =====================================================

    @Test
    void uploadImages_shouldPreserveOriginalErrorWhenCleanupFails() {

        mockExistingPost();

        when(imageStorageService.storeImage(any()))
                .thenReturn("uploads/post-images/first.png")
                .thenThrow(
                        new IllegalStateException("원래 업로드 오류")
                );

        when(postImageRepository.save(any(PostImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new IllegalStateException("파일 정리 오류"))
                .when(imageStorageService)
                .deleteImage("uploads/post-images/first.png");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(
                                createFile("first.png"),
                                createFile("second.png")
                        ),
                        EMAIL
                )
        );

        assertEquals(
                "원래 업로드 오류",
                exception.getMessage()
        );

        verify(imageStorageService).deleteImage(
                "uploads/post-images/first.png"
        );
    }

    // =====================================================
    // 15. DB 저장 실패 시 업로드 파일 정리
    // =====================================================

    @Test
    void uploadImages_shouldCleanupFileWhenDatabaseSaveFails() {

        mockExistingPost();

        when(imageStorageService.storeImage(any()))
                .thenReturn(IMAGE_PATH);

        when(postImageRepository.save(any(PostImage.class)))
                .thenThrow(
                        new IllegalStateException("DB 저장 실패")
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> postImageService.uploadImages(
                        POST_ID,
                        List.of(createFile("photo.png")),
                        EMAIL
                )
        );

        assertEquals(
                "DB 저장 실패",
                exception.getMessage()
        );

        verify(imageStorageService).deleteImage(IMAGE_PATH);
    }

    // =====================================================
    // 16. 존재하지 않는 게시글의 이미지 조회
    // =====================================================

    @Test
    void findImagesByPostId_shouldReturn404WhenPostMissing() {

        when(postRepository.existsById(POST_ID))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.findImagesByPostId(POST_ID)
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(postImageRepository);
    }

    // =====================================================
    // 17. 정상 이미지 목록 조회
    // =====================================================

    @Test
    void findImagesByPostId_shouldReturnImages() {

        when(postRepository.existsById(POST_ID))
                .thenReturn(true);

        PostImage image = createPostImage();

        when(postImageRepository.findByPostIdOrderByIdAsc(POST_ID))
                .thenReturn(List.of(image));

        List<PostImage> result =
                postImageService.findImagesByPostId(POST_ID);

        assertEquals(1, result.size());
        assertSame(image, result.get(0));
    }

    // =====================================================
    // 18. 존재하지 않는 게시글에서 이미지 삭제
    // =====================================================

    @Test
    void deleteImage_shouldReturn404WhenPostMissing() {

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.deleteImage(
                        POST_ID,
                        IMAGE_ID,
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 19. 작성자가 아닌 회원의 이미지 삭제 거부
    // =====================================================

    @Test
    void deleteImage_shouldRejectNonAuthor() {

        mockExistingPost();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.deleteImage(
                        POST_ID,
                        IMAGE_ID,
                        OTHER_EMAIL
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 20. 게시글 작성자 정보가 없으면 이미지 삭제 거부
    // =====================================================

    @Test
    void deleteImage_shouldRejectPostWithoutMember() {

        Post postWithoutMember = mock(Post.class);

        when(postWithoutMember.getMember())
                .thenReturn(null);

        when(postRepository.findById(POST_ID))
                .thenReturn(Optional.of(postWithoutMember));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.deleteImage(
                        POST_ID,
                        IMAGE_ID,
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 21. 존재하지 않는 이미지 삭제 요청
    // =====================================================

    @Test
    void deleteImage_shouldReturn404WhenImageMissing() {

        mockExistingPost();

        when(postImageRepository.findById(IMAGE_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.deleteImage(
                        POST_ID,
                        IMAGE_ID,
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 22. 다른 게시글에 등록된 이미지 삭제 거부
    // =====================================================

    @Test
    void deleteImage_shouldRejectImageFromDifferentPost() {

        mockExistingPost();

        // 이미지가 다른 게시글에 속한 상황을 Mock으로 설정
        PostImage image = mock(PostImage.class);
        Post differentPost = mock(Post.class);

        when(image.getPost())
                .thenReturn(differentPost);

        when(differentPost.getId())
                .thenReturn(999L);

        when(postImageRepository.findById(IMAGE_ID))
                .thenReturn(Optional.of(image));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postImageService.deleteImage(
                        POST_ID,
                        IMAGE_ID,
                        EMAIL
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(imageStorageService);
    }

    // =====================================================
    // 23. 정상 이미지 삭제
    // =====================================================

    @Test
    void deleteImage_shouldDeleteImageSuccessfully() {

        mockExistingPost();

        // 테스트용 이미지 및 게시글 Mock
        PostImage image = mock(PostImage.class);
        Post imagePost = mock(Post.class);

        when(image.getPost())
                .thenReturn(imagePost);

        when(imagePost.getId())
                .thenReturn(POST_ID);

        when(image.getImagePath())
                .thenReturn(IMAGE_PATH);

        when(postImageRepository.findById(IMAGE_ID))
                .thenReturn(Optional.of(image));

        // when
        postImageService.deleteImage(
                POST_ID,
                IMAGE_ID,
                EMAIL
        );

        // then: 파일과 DB 정보 모두 삭제
        verify(imageStorageService).deleteImage(IMAGE_PATH);
        verify(postImageRepository).delete(image);

        // 파일 삭제가 먼저 수행되었는지 확인
        org.mockito.InOrder inOrder = inOrder(
                imageStorageService,
                postImageRepository
        );

        inOrder.verify(imageStorageService)
                .deleteImage(IMAGE_PATH);

        inOrder.verify(postImageRepository)
                .delete(image);
    }
}
