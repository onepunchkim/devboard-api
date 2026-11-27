package com.example.devboard.service;

import com.example.devboard.entity.Member;
import com.example.devboard.entity.Post;
import com.example.devboard.entity.PostImage;
import com.example.devboard.repository.MemberRepository;
import com.example.devboard.repository.PostImageRepository;
import com.example.devboard.repository.PostRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostImageServiceIntegrationTest {

    // 이 테스트 클래스에서만 사용하는 이미지 저장소
    private static final Path UPLOAD_DIR = createTempUploadDirectory();

    private static Path createTempUploadDirectory() {
        try {
            return Files.createTempDirectory("devboard-post-image-service-")
                    .toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 테스트 환경의 이미지 저장 경로만 덮어쓴다.
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", UPLOAD_DIR::toString);
    }

    // DB의 상대 경로에서 실제 파일 경로를 구한다.
    private Path resolveImagePath(String imagePath) {
        assertThat(imagePath).startsWith("uploads/post-images/");
        return UPLOAD_DIR.resolve(Path.of(imagePath).getFileName()).normalize();
    }

    // DB 롤백과 별도로 실제 이미지 파일을 정리한다.
    @AfterEach
    void cleanUp() throws IOException {
        cleanUploadDirectory();
    }

    private static void cleanUploadDirectory() throws IOException {
        if (!Files.exists(UPLOAD_DIR)) {
            return;
        }
        List<Path> targets;
        try (Stream<Path> paths = Files.walk(UPLOAD_DIR)) {
            targets = paths.filter(path -> !path.equals(UPLOAD_DIR))
                    .sorted(Comparator.reverseOrder()).toList();
        }
        // Windows에서도 디렉터리 스트림을 닫은 뒤 삭제한다.
        for (Path target : targets) {
            Files.deleteIfExists(target);
        }
    }

    @AfterAll
    static void removeUploadDirectory() throws IOException {
        cleanUploadDirectory();
        Files.deleteIfExists(UPLOAD_DIR);
    }


    @Autowired
    private PostImageService postImageService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private EntityManager entityManager;

    // 테스트용 회원 생성
    private Member createTestMember(String email) {
        return memberRepository.save(
                new Member(email, "encoded-test-password", "이미지테스트회원")
        );
    }

    // 테스트용 게시글 생성
    private Post createTestPost(Member member) {
        return postRepository.save(
                new Post("이미지 테스트 게시글", "이미지 테스트 내용", member)
        );
    }

    // 실제 PNG 이미지 데이터를 생성
    private MockMultipartFile createTestImage(String filename)
            throws IOException {

        BufferedImage image = new BufferedImage(
                10, 10, BufferedImage.TYPE_INT_RGB
        );

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, "png", outputStream)).isTrue();
            return new MockMultipartFile(
                    "files", filename, "image/png", outputStream.toByteArray()
            );
        }
    }

    @Test
    void uploadImages_shouldSaveFileAndDatabase() throws IOException {
        // given: 회원과 게시글 생성
        Member member = createTestMember("image1@test.com");
        Post post = createTestPost(member);

        MockMultipartFile image = createTestImage("test.png");

        // when: 이미지 업로드
        List<PostImage> savedImages = postImageService.uploadImages(
                post.getId(),
                List.of(image),
                member.getEmail()
        );

        entityManager.flush();
        entityManager.clear();

        // then: DB 저장 확인
        assertThat(savedImages).hasSize(1);

        PostImage savedImage = postImageRepository
                .findById(savedImages.get(0).getId())
                .orElseThrow();

        assertThat(savedImage.getOriginalFilename())
                .isEqualTo("test.png");

        assertThat(savedImage.getPost().getId())
                .isEqualTo(post.getId());

        // 실제 파일 저장 확인
        Path filePath = resolveImagePath(savedImage.getImagePath());

        assertThat(Files.exists(filePath)).isTrue();
        // 실제 저장된 바이트가 원본과 동일한지 확인
        assertThat(Files.readAllBytes(filePath)).containsExactly(image.getBytes());
        assertThat(savedImage.getCreatedAt()).isNotNull();
    }

    @Test
    void findImagesByPostId_shouldReturnImages() throws IOException {
        // given: 회원과 게시글 생성
        Member member = createTestMember("image2@test.com");
        Post post = createTestPost(member);

        postImageService.uploadImages(
                post.getId(),
                List.of(
                        createTestImage("first.png"),
                        createTestImage("second.png")
                ),
                member.getEmail()
        );

        // when: 이미지 목록 조회
        List<PostImage> images =
                postImageService.findImagesByPostId(post.getId());

        // then: 이미지 목록 확인
        assertThat(images).hasSize(2);

        assertThat(images)
                .extracting(PostImage::getOriginalFilename)
                .containsExactly("first.png", "second.png");
    }

    @Test
    void deleteImage_shouldDeleteFileAndDatabase() throws IOException {
        // given: 이미지 업로드
        Member member = createTestMember("image3@test.com");
        Post post = createTestPost(member);

        List<PostImage> images = postImageService.uploadImages(
                post.getId(),
                List.of(createTestImage("delete.png")),
                member.getEmail()
        );

        Long imageId = images.get(0).getId();

        String imagePath = images.get(0).getImagePath();

        Path filePath = resolveImagePath(imagePath);

        assertThat(Files.exists(filePath)).isTrue();

        // when: 이미지 삭제
        postImageService.deleteImage(
                post.getId(),
                imageId,
                member.getEmail()
        );

        entityManager.flush();
        entityManager.clear();

        // then: DB 정보 삭제 확인
        assertThat(postImageRepository.existsById(imageId))
                .isFalse();

        // 실제 파일 삭제 확인
        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    void uploadImages_shouldRejectOtherMember() throws IOException {
        // given: 게시글 작성자와 다른 회원 생성
        Member writer = createTestMember("image4@test.com");
        Member otherMember = createTestMember("other4@test.com");

        Post post = createTestPost(writer);

        // when & then: 다른 회원이 이미지 업로드 시도
        assertThatThrownBy(() ->
                postImageService.uploadImages(
                        post.getId(),
                        List.of(createTestImage("forbidden.png")),
                        otherMember.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.FORBIDDEN
                );

        assertThat(postImageRepository.countByPostId(post.getId()))
                .isZero();
    }

    @Test
    void deleteImage_shouldRejectOtherMember() throws IOException {
        // given: 게시글 작성자가 이미지 업로드
        Member writer = createTestMember("image5@test.com");
        Member otherMember = createTestMember("other5@test.com");

        Post post = createTestPost(writer);

        List<PostImage> images = postImageService.uploadImages(
                post.getId(),
                List.of(createTestImage("protected.png")),
                writer.getEmail()
        );

        PostImage image = images.get(0);

        // when & then: 다른 회원이 이미지 삭제 시도
        assertThatThrownBy(() ->
                postImageService.deleteImage(
                        post.getId(),
                        image.getId(),
                        otherMember.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.FORBIDDEN
                );

        // DB 이미지 정보 유지 확인
        assertThat(postImageRepository.existsById(image.getId()))
                .isTrue();
        assertThat(resolveImagePath(image.getImagePath())).exists();
    }

    @Test
    void uploadImages_shouldRejectMoreThanFiveImages() throws IOException {
        // given: 회원과 게시글 생성
        Member member = createTestMember("image6@test.com");
        Post post = createTestPost(member);

        // MultipartFile 타입으로 선언하여 서비스 메서드의 매개변수와 일치시킨다.
        List<MultipartFile> images = List.of(
                createTestImage("1.png"),
                createTestImage("2.png"),
                createTestImage("3.png"),
                createTestImage("4.png"),
                createTestImage("5.png"),
                createTestImage("6.png")
        );

        // when & then: 6장 업로드 시도
        assertThatThrownBy(() ->
                postImageService.uploadImages(
                        post.getId(),
                        images,
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.BAD_REQUEST
                );

        assertThat(postImageRepository.countByPostId(post.getId()))
                .isZero();
    }

    @Test
    void uploadImages_shouldRejectEmptyFile() throws IOException {
        // given
        Member member = createTestMember("image7@test.com");
        Post post = createTestPost(member);

        MockMultipartFile emptyFile = new MockMultipartFile(
                "files",
                "empty.png",
                "image/png",
                new byte[0]
        );

        // when & then
        assertThatThrownBy(() ->
                postImageService.uploadImages(
                        post.getId(),
                        List.of(emptyFile),
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.BAD_REQUEST
                );

        assertThat(postImageRepository.countByPostId(post.getId()))
                .isZero();
    }

    @Test
    void findImagesByPostId_shouldRejectMissingPost() {
        // given: 존재하지 않는 게시글 ID
        Long invalidPostId = -1L;

        // when & then
        assertThatThrownBy(() ->
                postImageService.findImagesByPostId(invalidPostId)
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.NOT_FOUND
                );
    }


    // ==========================================
    // 추가 테스트 1: 이미지 목록이 null인 경우
    // ==========================================

    @Test
    void uploadImages_shouldRejectNullFileList() {
        Member member = createTestMember("image8@test.com");
        Post post = createTestPost(member);

        // 업로드 목록이 null이면 400 반환
        assertThatThrownBy(() ->
                postImageService.uploadImages(
                        post.getId(),
                        null,
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.BAD_REQUEST
                );
    }

    // ==========================================
    // 추가 테스트 2: 이미지 목록이 비어 있는 경우
    // ==========================================

    @Test
    void uploadImages_shouldRejectEmptyFileList() {
        Member member = createTestMember("image9@test.com");
        Post post = createTestPost(member);

        // 빈 목록은 업로드할 이미지가 없는 요청
        assertThatThrownBy(() ->
                postImageService.uploadImages(
                        post.getId(),
                        List.of(),
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.BAD_REQUEST
                );
    }

    // ==========================================
    // 추가 테스트 3: 존재하지 않는 게시글에 업로드
    // ==========================================

    @Test
    void uploadImages_shouldRejectMissingPost() throws IOException {
        Member member = createTestMember("image10@test.com");

        // 존재하지 않는 게시글 ID
        Long invalidPostId = -1L;

        assertThatThrownBy(() ->
                postImageService.uploadImages(
                        invalidPostId,
                        List.of(createTestImage("missing.png")),
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.NOT_FOUND
                );
    }

    // ==========================================
    // 추가 테스트 4: null 파일이 포함된 경우
    // ==========================================

    @Test
    void uploadImages_shouldRejectNullFile() throws IOException {
        Member member = createTestMember("image11@test.com");
        Post post = createTestPost(member);

        // List.of()는 null을 허용하지 않으므로
        // Arrays.asList()를 사용한다.
        List<MultipartFile> files = java.util.Arrays.asList(
                createTestImage("valid.png"),
                null
        );

        assertThatThrownBy(() ->
                postImageService.uploadImages(
                        post.getId(),
                        files,
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.BAD_REQUEST
                );

        // 이미지 정보가 저장되지 않았는지 확인
        assertThat(postImageRepository.countByPostId(post.getId()))
                .isZero();
    }

    // ==========================================
    // 추가 테스트 5: 존재하지 않는 이미지 삭제
    // ==========================================

    @Test
    void deleteImage_shouldRejectMissingImage() {
        Member member = createTestMember("image12@test.com");
        Post post = createTestPost(member);

        Long invalidImageId = -1L;

        assertThatThrownBy(() ->
                postImageService.deleteImage(
                        post.getId(),
                        invalidImageId,
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.NOT_FOUND
                );
    }

    // ==========================================
    // 추가 테스트 6: 다른 게시글의 이미지 삭제 차단
    // ==========================================

    @Test
    void deleteImage_shouldRejectImageFromAnotherPost()
            throws IOException {

        Member member = createTestMember("image13@test.com");

        // 동일 회원이 작성한 서로 다른 게시글
        Post firstPost = createTestPost(member);
        Post secondPost = createTestPost(member);

        // 첫 번째 게시글에 이미지 업로드
        List<PostImage> images = postImageService.uploadImages(
                firstPost.getId(),
                List.of(createTestImage("first-post.png")),
                member.getEmail()
        );

        Long imageId = images.get(0).getId();

        // 두 번째 게시글의 이미지인 것처럼 삭제 요청
        assertThatThrownBy(() ->
                postImageService.deleteImage(
                        secondPost.getId(),
                        imageId,
                        member.getEmail()
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.BAD_REQUEST
                );

        // 원래 게시글의 이미지 정보가 유지되어야 한다.
        assertThat(postImageRepository.existsById(imageId))
                .isTrue();
        assertThat(resolveImagePath(images.get(0).getImagePath())).exists();
    }

}
