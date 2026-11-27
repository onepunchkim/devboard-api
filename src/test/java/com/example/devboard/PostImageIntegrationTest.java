package com.example.devboard;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostImage;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostRepository;
import com.example.devboard.repository.PostImageRepository;
import com.example.devboard.service.JwtService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostImageIntegrationTest {

    // 게시글 이미지 API 공통 URL
    private static final String IMAGE_URL =
            "/api/posts/{postId}/images";

    // 개별 이미지 삭제 URL
    private static final String DELETE_IMAGE_URL =
            "/api/posts/{postId}/images/{imageId}";

    // DB에 저장되는 이미지 상대 경로의 접두사
    private static final String IMAGE_PREFIX =
            "uploads/post-images/";

    // 이미지 파일 한 개의 최대 크기
    private static final int MAX_FILE_SIZE =
            5 * 1024 * 1024;

    // 이 테스트 클래스에서만 사용하는 임시 저장소
    private static final Path UPLOAD_DIR =
            createTempUploadDirectory();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 테스트가 직접 생성한 회원 및 게시글 ID
    private final List<Long> createdMemberIds =
            new ArrayList<>();

    private final List<Long> createdPostIds =
            new ArrayList<>();

    // 테스트용 회원 및 게시글
    private Member writer;
    private Member anotherMember;

    private Long postId;

    // 실제 JWT 인증 토큰
    private String writerToken;
    private String anotherMemberToken;

    // ==========================================
    // 테스트 환경 설정
    // ==========================================

    // 테스트 전용 임시 디렉터리 생성
    private static Path createTempUploadDirectory() {

        try {
            return Files.createTempDirectory(
                    "devboard-post-image-api-"
            ).toAbsolutePath().normalize();

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "테스트용 이미지 저장소 생성 실패",
                    e
            );
        }
    }

    // application-test.yml의 이미지 경로를
    // 이 테스트 클래스에서만 덮어쓴다.
    @DynamicPropertySource
    static void registerProperties(
            DynamicPropertyRegistry registry
    ) {

        registry.add(
                "file.upload-dir",
                UPLOAD_DIR::toString
        );
    }

    @BeforeEach
    void setUp() {

        // 게시글 작성자 생성
        writer = createTestMember("작성자");

        // 작성자가 아닌 다른 회원 생성
        anotherMember = createTestMember("다른회원");

        // 작성자의 게시글 생성
        Post post = createTestPost(
                writer,
                "이미지 API 통합 테스트 게시글"
        );

        postId = post.getId();

        // 실제 JWT 토큰 생성
        writerToken = jwtService.generateToken(
                writer.getId(),
                writer.getEmail()
        );

        anotherMemberToken = jwtService.generateToken(
                anotherMember.getId(),
                anotherMember.getEmail()
        );
    }

    // 각 테스트 종료 후 생성한 데이터와 파일 정리
    @AfterEach
    void tearDown() throws IOException {

        try {

            // 외래 키 제약 조건을 고려하여
            // 이미지 → 게시글 → 회원 순서로 삭제
            for (Long createdPostId : createdPostIds) {

                List<PostImage> images =
                        postImageRepository
                                .findByPostIdOrderByIdAsc(
                                        createdPostId
                                );

                postImageRepository.deleteAll(images);
            }

            postRepository.deleteAllById(createdPostIds);

            memberRepository.deleteAllById(
                    createdMemberIds
            );

        } finally {

            createdPostIds.clear();
            createdMemberIds.clear();

            // DB 정리에 실패하더라도 파일 정리 시도
            cleanUploadDirectory();
        }
    }

    // 모든 테스트가 종료되면 임시 디렉터리 제거
    @AfterAll
    static void removeUploadDirectory()
            throws IOException {

        cleanUploadDirectory();

        Files.deleteIfExists(UPLOAD_DIR);
    }

    // 임시 디렉터리 내부의 파일만 삭제
    private static void cleanUploadDirectory()
            throws IOException {

        if (!Files.exists(UPLOAD_DIR)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(UPLOAD_DIR)) {

            List<Path> targets = paths
                    .filter(path -> !path.equals(UPLOAD_DIR))
                    .sorted(Comparator.reverseOrder())
                    .toList();

            for (Path target : targets) {
                Files.deleteIfExists(target);
            }
        }
    }

    // ==========================================
    // 테스트 공통 메서드
    // ==========================================

    // 테스트용 회원 생성
    private Member createTestMember(String nickname) {

        // 다른 테스트와 이메일이 충돌하지 않도록 UUID 사용
        String email = "image-api-"
                + UUID.randomUUID()
                + "@test.com";

        Member member = memberRepository.saveAndFlush(
                new Member(
                        email,
                        passwordEncoder.encode("password123"),
                        nickname
                )
        );

        createdMemberIds.add(member.getId());

        return member;
    }

    // 테스트용 게시글 생성
    private Post createTestPost(
            Member member,
            String title
    ) {

        Post post = postRepository.saveAndFlush(
                new Post(
                        title,
                        "게시글 이미지 API 통합 테스트 내용",
                        member
                )
        );

        createdPostIds.add(post.getId());

        return post;
    }

    // Authorization 헤더에 사용할 Bearer 토큰 생성
    private String bearerToken(String token) {

        return "Bearer " + token;
    }

    // 실제 PNG 이미지 데이터를 생성한다.
    private byte[] createPngBytes() throws IOException {

        BufferedImage image = new BufferedImage(
                20,
                20,
                BufferedImage.TYPE_INT_RGB
        );

        try (ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            boolean written = ImageIO.write(
                    image,
                    "png",
                    outputStream
            );

            assertThat(written).isTrue();

            return outputStream.toByteArray();
        }
    }

    // 정상적인 Multipart 이미지 파일 생성
    private MockMultipartFile createImageFile(
            String filename
    ) throws IOException {

        return new MockMultipartFile(
                "files",
                filename,
                "image/png",
                createPngBytes()
        );
    }

    // 지정한 개수만큼 이미지 파일 생성
    private MockMultipartFile[] createImageFiles(
            int count
    ) throws IOException {

        MockMultipartFile[] files =
                new MockMultipartFile[count];

        for (int i = 0; i < count; i++) {

            files[i] = createImageFile(
                    "image-" + (i + 1) + ".png"
            );
        }

        return files;
    }

    // 이미지 업로드 API 요청 공통 메서드
    private ResultActions uploadImages(
            Long targetPostId,
            String token,
            MockMultipartFile... files
    ) throws Exception {

        MockMultipartHttpServletRequestBuilder request =
                multipart(IMAGE_URL, targetPostId);

        // 하나의 요청에 여러 이미지 파일 추가
        for (MockMultipartFile file : files) {
            request.file(file);
        }

        // 토큰이 있는 경우에만 Authorization 헤더 추가
        if (token != null) {

            request.header(
                    HttpHeaders.AUTHORIZATION,
                    bearerToken(token)
            );
        }

        return mockMvc.perform(request);
    }

    // DB에 저장된 이미지 목록 조회
    private List<PostImage> findSavedImages(
            Long targetPostId
    ) {

        return postImageRepository
                .findByPostIdOrderByIdAsc(targetPostId);
    }

    // 이미지 업로드 성공 후 저장된 이미지 반환
    private PostImage uploadSingleImage(
            Long targetPostId,
            String filename
    ) throws Exception {

        uploadImages(
                targetPostId,
                writerToken,
                createImageFile(filename)
        ).andExpect(status().isOk());

        List<PostImage> images =
                findSavedImages(targetPostId);

        assertThat(images).hasSize(1);

        return images.get(0);
    }

    // DB의 상대 경로를 실제 파일 경로로 변환
    private Path resolveImagePath(String imagePath) {

        assertThat(imagePath)
                .startsWith(IMAGE_PREFIX);

        Path targetPath = UPLOAD_DIR.resolve(
                Path.of(imagePath).getFileName()
        ).normalize();

        assertThat(targetPath.startsWith(UPLOAD_DIR))
                .isTrue();

        return targetPath;
    }

    // 실제 저장된 파일 개수 조회
    private long countStoredFiles() throws IOException {

        try (Stream<Path> paths = Files.list(UPLOAD_DIR)) {

            return paths
                    .filter(Files::isRegularFile)
                    .count();
        }
    }

    // DB와 파일 시스템에 이미지가 없는지 확인
    private void assertNoImages() throws IOException {

        assertThat(
                postImageRepository.countByPostId(postId)
        ).isZero();

        assertThat(countStoredFiles()).isZero();
    }

    // ==========================================
    // 1. 이미지 업로드 성공
    // ==========================================

    @Test
    void uploadImage_shouldSaveFileAndDatabase()
            throws Exception {

        // given: 정상적인 PNG 파일 생성
        MockMultipartFile file =
                createImageFile("test.png");

        // when & then: 이미지 업로드 요청
        uploadImages(postId, writerToken, file)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].imagePath").exists())
                .andExpect(jsonPath("$[0].originalFilename")
                        .value("test.png"));

        // DB에 저장된 이미지 확인
        List<PostImage> images = findSavedImages(postId);

        assertThat(images).hasSize(1);

        PostImage savedImage = images.get(0);

        assertThat(savedImage.getId()).isNotNull();

        assertThat(savedImage.getOriginalFilename())
                .isEqualTo("test.png");

        assertThat(savedImage.getCreatedAt()).isNotNull();

        assertThat(savedImage.getImagePath())
                .startsWith(IMAGE_PREFIX)
                .endsWith(".png");

        // 실제 파일 존재 여부 확인
        Path actualPath =
                resolveImagePath(savedImage.getImagePath());

        assertThat(actualPath).exists();

        // 원본 데이터와 저장된 데이터가 동일한지 확인
        assertThat(Files.readAllBytes(actualPath))
                .containsExactly(file.getBytes());

        assertThat(countStoredFiles()).isEqualTo(1);
    }

    // ==========================================
    // 2. 여러 이미지 업로드 성공
    // ==========================================

    @Test
    void uploadImages_shouldSaveMultipleImages()
            throws Exception {

        uploadImages(
                postId,
                writerToken,
                createImageFile("first.png"),
                createImageFile("second.png"),
                createImageFile("third.png")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].originalFilename")
                        .value("first.png"))
                .andExpect(jsonPath("$[1].originalFilename")
                        .value("second.png"))
                .andExpect(jsonPath("$[2].originalFilename")
                        .value("third.png"));

        List<PostImage> images = findSavedImages(postId);

        assertThat(images).hasSize(3);

        // 각 이미지에 서로 다른 저장 경로가 생성되는지 확인
        assertThat(images)
                .extracting(PostImage::getImagePath)
                .doesNotHaveDuplicates();

        for (PostImage image : images) {

            assertThat(
                    resolveImagePath(image.getImagePath())
            ).exists();
        }

        assertThat(countStoredFiles()).isEqualTo(3);
    }

    // ==========================================
    // 3. 이미지 목록 조회
    // ==========================================

    @Test
    void getImages_shouldReturnImagesInOrder()
            throws Exception {

        // given: 이미지 2장 업로드
        uploadImages(
                postId,
                writerToken,
                createImageFile("first.png"),
                createImageFile("second.png")
        ).andExpect(status().isOk());

        List<PostImage> images = findSavedImages(postId);

        assertThat(images).hasSize(2);

        // when & then: 등록 순서대로 반환되는지 확인
        mockMvc.perform(
                        get(IMAGE_URL, postId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearerToken(writerToken)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id")
                        .value(images.get(0).getId()))
                .andExpect(jsonPath("$[0].originalFilename")
                        .value("first.png"))
                .andExpect(jsonPath("$[0].imagePath")
                        .value(images.get(0).getImagePath()))
                .andExpect(jsonPath("$[1].id")
                        .value(images.get(1).getId()))
                .andExpect(jsonPath("$[1].originalFilename")
                        .value("second.png"))
                .andExpect(jsonPath("$[1].imagePath")
                        .value(images.get(1).getImagePath()));
    }

    // ==========================================
    // 4. 이미지가 없는 게시글 조회
    // ==========================================

    @Test
    void getImages_shouldReturnEmptyList()
            throws Exception {

        mockMvc.perform(
                        get(IMAGE_URL, postId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearerToken(writerToken)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        assertNoImages();
    }

    // ==========================================
    // 5. 이미지 삭제 성공
    // ==========================================

    @Test
    void deleteImage_shouldDeleteFileAndDatabase()
            throws Exception {

        PostImage image = uploadSingleImage(
                postId,
                "delete.png"
        );

        Path actualPath =
                resolveImagePath(image.getImagePath());

        assertThat(actualPath).exists();

        // 이미지 삭제 요청
        mockMvc.perform(
                        delete(
                                DELETE_IMAGE_URL,
                                postId,
                                image.getId()
                        ).header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(writerToken)
                        )
                )
                .andExpect(status().isNoContent());

        // DB 이미지 정보 삭제 확인
        assertThat(
                postImageRepository.existsById(image.getId())
        ).isFalse();

        // 실제 이미지 파일 삭제 확인
        assertThat(actualPath).doesNotExist();

        // 게시글 자체는 유지되어야 한다.
        assertThat(postRepository.existsById(postId))
                .isTrue();

        assertNoImages();
    }

    // ==========================================
    // 6. 다른 회원의 이미지 업로드 차단
    // ==========================================

    @Test
    void uploadImages_byOtherMember_returnsForbidden()
            throws Exception {

        uploadImages(
                postId,
                anotherMemberToken,
                createImageFile("forbidden.png")
        ).andExpect(status().isForbidden());

        // DB와 파일 시스템 모두 변경되지 않아야 한다.
        assertNoImages();
    }

    // ==========================================
    // 7. 다른 회원의 이미지 삭제 차단
    // ==========================================

    @Test
    void deleteImage_byOtherMember_returnsForbidden()
            throws Exception {

        PostImage image = uploadSingleImage(
                postId,
                "protected.png"
        );

        Path actualPath =
                resolveImagePath(image.getImagePath());

        mockMvc.perform(
                        delete(
                                DELETE_IMAGE_URL,
                                postId,
                                image.getId()
                        ).header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(anotherMemberToken)
                        )
                )
                .andExpect(status().isForbidden());

        // 기존 이미지 정보와 파일 유지 확인
        assertThat(
                postImageRepository.existsById(image.getId())
        ).isTrue();

        assertThat(actualPath).exists();
    }

    // ==========================================
    // 8. JWT 없이 이미지 업로드 차단
    // ==========================================

    @Test
    void uploadImages_withoutToken_returnsUnauthorized()
            throws Exception {

        uploadImages(
                postId,
                null,
                createImageFile("unauthorized.png")
        ).andExpect(status().isUnauthorized());

        assertNoImages();
    }

    // ==========================================
    // 9. JWT 없이 이미지 삭제 차단
    // ==========================================

    @Test
    void deleteImage_withoutToken_returnsUnauthorized()
            throws Exception {

        PostImage image = uploadSingleImage(
                postId,
                "protected.png"
        );

        mockMvc.perform(
                        delete(
                                DELETE_IMAGE_URL,
                                postId,
                                image.getId()
                        )
                )
                .andExpect(status().isUnauthorized());

        assertThat(
                postImageRepository.existsById(image.getId())
        ).isTrue();

        assertThat(
                resolveImagePath(image.getImagePath())
        ).exists();
    }

    // ==========================================
    // 10. 존재하지 않는 게시글에 업로드
    // ==========================================

    @Test
    void uploadImages_missingPost_returnsNotFound()
            throws Exception {

        uploadImages(
                -1L,
                writerToken,
                createImageFile("missing.png")
        ).andExpect(status().isNotFound());

        assertNoImages();
    }

    // ==========================================
    // 11. 존재하지 않는 게시글 이미지 조회
    // ==========================================

    @Test
    void getImages_missingPost_returnsNotFound()
            throws Exception {

        mockMvc.perform(
                        get(IMAGE_URL, -1L)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearerToken(writerToken)
                                )
                )
                .andExpect(status().isNotFound());
    }

    // ==========================================
    // 12. 존재하지 않는 이미지 삭제
    // ==========================================

    @Test
    void deleteImage_missingImage_returnsNotFound()
            throws Exception {

        mockMvc.perform(
                        delete(
                                DELETE_IMAGE_URL,
                                postId,
                                -1L
                        ).header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(writerToken)
                        )
                )
                .andExpect(status().isNotFound());

        assertNoImages();
    }

    // ==========================================
    // 13. 빈 이미지 파일 업로드 차단
    // ==========================================

    @Test
    void uploadImages_emptyFile_returnsBadRequest()
            throws Exception {

        MockMultipartFile emptyFile =
                new MockMultipartFile(
                        "files",
                        "empty.png",
                        "image/png",
                        new byte[0]
                );

        uploadImages(
                postId,
                writerToken,
                emptyFile
        ).andExpect(status().isBadRequest());

        assertNoImages();
    }

    // ==========================================
    // 14. 허용되지 않은 확장자 차단
    // ==========================================

    @Test
    void uploadImages_invalidExtension_returnsBadRequest()
            throws Exception {

        // 실제 PNG 데이터이지만 확장자는 txt
        MockMultipartFile invalidFile =
                new MockMultipartFile(
                        "files",
                        "invalid.txt",
                        "text/plain",
                        createPngBytes()
                );

        uploadImages(
                postId,
                writerToken,
                invalidFile
        ).andExpect(status().isBadRequest());

        assertNoImages();
    }

    // ==========================================
    // 15. 이미지로 위장한 일반 파일 차단
    // ==========================================

    @Test
    void uploadImages_fakeImage_returnsBadRequest()
            throws Exception {

        // 확장자는 png지만 실제 이미지 데이터가 아니다.
        MockMultipartFile fakeImage =
                new MockMultipartFile(
                        "files",
                        "fake.png",
                        "image/png",
                        new byte[]{1, 2, 3, 4, 5}
                );

        uploadImages(
                postId,
                writerToken,
                fakeImage
        ).andExpect(status().isBadRequest());

        assertNoImages();
    }

    // ==========================================
    // 16. 5MB 초과 이미지 차단
    // ==========================================

    @Test
    void uploadImages_oversizedFile_returnsBadRequest()
            throws Exception {

        // 최대 허용 크기보다 1byte 큰 파일
        MockMultipartFile oversizedFile =
                new MockMultipartFile(
                        "files",
                        "large.png",
                        "image/png",
                        new byte[MAX_FILE_SIZE + 1]
                );

        uploadImages(
                postId,
                writerToken,
                oversizedFile
        ).andExpect(status().isBadRequest());

        assertNoImages();
    }

    // ==========================================
    // 17. 이미지 최대 5장 등록 성공
    // ==========================================

    @Test
    void uploadImages_shouldAllowExactlyFiveImages()
            throws Exception {

        uploadImages(
                postId,
                writerToken,
                createImageFiles(5)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        assertThat(
                postImageRepository.countByPostId(postId)
        ).isEqualTo(5);

        assertThat(countStoredFiles()).isEqualTo(5);
    }

    // ==========================================
    // 18. 한 번에 이미지 6장 업로드 차단
    // ==========================================

    @Test
    void uploadImages_moreThanFive_returnsBadRequest()
            throws Exception {

        uploadImages(
                postId,
                writerToken,
                createImageFiles(6)
        ).andExpect(status().isBadRequest());

        assertNoImages();
    }

    // ==========================================
    // 19. 누적 이미지 개수 제한 검증
    // ==========================================

    @Test
    void uploadImages_shouldEnforceTotalImageLimit()
            throws Exception {

        // 먼저 이미지 3장 등록
        uploadImages(
                postId,
                writerToken,
                createImageFiles(3)
        ).andExpect(status().isOk());

        // 추가 이미지 2장 등록: 총 5장
        uploadImages(
                postId,
                writerToken,
                createImageFiles(2)
        ).andExpect(status().isOk());

        List<PostImage> originalImages =
                findSavedImages(postId);

        assertThat(originalImages).hasSize(5);

        // 추가 이미지 1장 등록 시도: 총 6장
        uploadImages(
                postId,
                writerToken,
                createImageFile("overflow.png")
        ).andExpect(status().isBadRequest());

        // 기존 이미지 5장은 유지되어야 한다.
        assertThat(
                postImageRepository.countByPostId(postId)
        ).isEqualTo(5);

        assertThat(countStoredFiles()).isEqualTo(5);

        assertThat(findSavedImages(postId))
                .extracting(PostImage::getId)
                .containsExactlyElementsOf(
                        originalImages.stream()
                                .map(PostImage::getId)
                                .toList()
                );

        for (PostImage image : originalImages) {

            assertThat(
                    resolveImagePath(image.getImagePath())
            ).exists();
        }
    }

    // ==========================================
    // 20. 다른 게시글에 속한 이미지 삭제 차단
    // ==========================================

    @Test
    void deleteImage_fromAnotherPost_returnsBadRequest()
            throws Exception {

        // 동일 회원이 작성한 두 번째 게시글
        Post anotherPost = createTestPost(
                writer,
                "두 번째 게시글"
        );

        // 첫 번째 게시글에 이미지 등록
        PostImage image = uploadSingleImage(
                postId,
                "first-post.png"
        );

        // 두 번째 게시글의 이미지인 것처럼 삭제 요청
        mockMvc.perform(
                        delete(
                                DELETE_IMAGE_URL,
                                anotherPost.getId(),
                                image.getId()
                        ).header(
                                HttpHeaders.AUTHORIZATION,
                                bearerToken(writerToken)
                        )
                )
                .andExpect(status().isBadRequest());

        // 첫 번째 게시글의 이미지 유지 확인
        assertThat(
                postImageRepository.existsById(image.getId())
        ).isTrue();

        assertThat(
                postImageRepository.countByPostId(postId)
        ).isEqualTo(1);

        assertThat(
                postImageRepository.countByPostId(
                        anotherPost.getId()
                )
        ).isZero();

        assertThat(
                resolveImagePath(image.getImagePath())
        ).exists();
    }

    // ==========================================
    // 21. 게시글 삭제 시 관련 이미지 정리
    // ==========================================

    @Test
    void deletePost_shouldDeleteRelatedImages()
            throws Exception {

        // 첫 번째 게시글에 이미지 2장 등록
        uploadImages(
                postId,
                writerToken,
                createImageFile("first.png"),
                createImageFile("second.png")
        ).andExpect(status().isOk());

        List<PostImage> images = findSavedImages(postId);

        assertThat(images).hasSize(2);

        // 삭제 대상 이미지의 실제 경로 보관
        List<Path> deletedImagePaths = images.stream()
                .map(PostImage::getImagePath)
                .map(this::resolveImagePath)
                .toList();

        // 다른 게시글과 이미지 생성
        Post anotherPost = createTestPost(
                writer,
                "유지되어야 하는 게시글"
        );

        PostImage anotherImage = uploadSingleImage(
                anotherPost.getId(),
                "preserved.png"
        );

        // 첫 번째 게시글 삭제
        mockMvc.perform(
                        delete("/api/posts/{id}", postId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearerToken(writerToken)
                                )
                )
                .andExpect(status().isNoContent());

        // 게시글 삭제 확인
        assertThat(postRepository.existsById(postId))
                .isFalse();

        // 관련 이미지 정보 삭제 확인
        assertThat(
                postImageRepository.countByPostId(postId)
        ).isZero();

        // 실제 이미지 파일 삭제 확인
        for (Path path : deletedImagePaths) {
            assertThat(path).doesNotExist();
        }

        // 다른 게시글과 이미지는 유지되어야 한다.
        assertThat(
                postRepository.existsById(anotherPost.getId())
        ).isTrue();

        assertThat(
                postImageRepository.existsById(
                        anotherImage.getId()
                )
        ).isTrue();

        assertThat(
                resolveImagePath(anotherImage.getImagePath())
        ).exists();

        assertThat(countStoredFiles()).isEqualTo(1);
    }

    // ==========================================
    // 22. 일부 이미지 업로드 실패 시 롤백
    // ==========================================

    @Test
    void uploadImages_partialFailure_shouldRollback()
            throws Exception {

        // 첫 번째 파일은 정상 이미지
        MockMultipartFile validFile =
                createImageFile("valid.png");

        // 두 번째 파일은 허용되지 않은 확장자
        MockMultipartFile invalidFile =
                new MockMultipartFile(
                        "files",
                        "invalid.txt",
                        "text/plain",
                        new byte[]{1, 2, 3}
                );

        // 첫 번째 파일 저장 후 두 번째 파일에서 실패하도록 구성
        uploadImages(
                postId,
                writerToken,
                validFile,
                invalidFile
        ).andExpect(status().isBadRequest());

        // 서비스 트랜잭션 종료 후 DB 재조회
        // 첫 번째 이미지 정보도 롤백되어야 한다.
        assertThat(
                postImageRepository.countByPostId(postId)
        ).isZero();

        // 첫 번째 이미지 파일도 정리되어야 한다.
        assertThat(countStoredFiles()).isZero();

        // 게시글 자체는 정상적으로 유지되어야 한다.
        assertThat(postRepository.existsById(postId))
                .isTrue();
    }
}