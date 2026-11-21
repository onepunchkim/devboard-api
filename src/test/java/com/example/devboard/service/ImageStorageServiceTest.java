package com.example.devboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ImageStorageServiceTest {

    // 이미지 최대 허용 크기: 5MB
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

    // DB에 저장되는 이미지 상대 경로
    private static final String IMAGE_PREFIX =
            "uploads/post-images/";

    // 테스트마다 독립적인 임시 디렉터리 생성
    @TempDir
    Path tempDir;

    private ImageStorageService imageStorageService;

    @BeforeEach
    void setUp() {

        // 실제 운영 업로드 폴더를 사용하지 않는다.
        imageStorageService = new ImageStorageService(
                tempDir.toString()
        );
    }

    // =====================================================
    // 1. 테스트용 이미지 및 파일 생성
    // =====================================================

    // 실제로 디코딩 가능한 PNG 이미지 생성
    private byte[] createPngBytes() throws IOException {

        BufferedImage image = new BufferedImage(
                10,
                10,
                BufferedImage.TYPE_INT_RGB
        );

        try (ByteArrayOutputStream output =
                     new ByteArrayOutputStream()) {

            assertTrue(ImageIO.write(image, "png", output));

            return output.toByteArray();
        }
    }

    // 테스트용 MultipartFile 생성
    private MockMultipartFile createFile(
            String filename,
            byte[] content
    ) {
        return new MockMultipartFile(
                "file",
                filename,
                "image/png",
                content
        );
    }

    // 이미지 업로드 후 실제 저장된 파일 경로 반환
    private Path getStoredFilePath(String imagePath) {

        assertTrue(imagePath.startsWith(IMAGE_PREFIX));

        String filename = imagePath.substring(
                IMAGE_PREFIX.length()
        );

        return tempDir.resolve(filename);
    }

    // HTTP 상태 코드 검증
    private void assertStatus(
            HttpStatus expectedStatus,
            ResponseStatusException exception
    ) {
        assertEquals(
                expectedStatus,
                exception.getStatusCode()
        );
    }

    // =====================================================
    // 2. 정상 이미지 업로드 테스트
    // =====================================================

    @Test
    void storeImageSuccessfully() throws IOException {

        // given
        MockMultipartFile file = createFile(
                "test.png",
                createPngBytes()
        );

        // when
        String imagePath = imageStorageService.storeImage(file);

        // then
        assertTrue(imagePath.startsWith(IMAGE_PREFIX));
        assertTrue(imagePath.endsWith(".png"));

        // 실제 파일 저장 확인
        Path savedFile = getStoredFilePath(imagePath);

        assertTrue(Files.isRegularFile(savedFile));

        // 저장된 이미지가 실제로 디코딩되는지 확인
        assertNotNull(ImageIO.read(savedFile.toFile()));
    }

    @Test
    void storeImageUsesUniqueFilename() throws IOException {

        // given
        MockMultipartFile file = createFile(
                "test.png",
                createPngBytes()
        );

        // when: 동일한 파일을 두 번 업로드
        String first = imageStorageService.storeImage(file);
        String second = imageStorageService.storeImage(file);

        // then: UUID 파일명이 달라야 한다.
        assertNotEquals(first, second);

        assertTrue(Files.exists(getStoredFilePath(first)));
        assertTrue(Files.exists(getStoredFilePath(second)));
    }

    @Test
    void storeImageAcceptsUppercaseExtension()
            throws IOException {

        // given
        MockMultipartFile file = createFile(
                "test.PNG",
                createPngBytes()
        );

        // when
        String imagePath = imageStorageService.storeImage(file);

        // then: 확장자는 소문자로 저장
        assertTrue(imagePath.endsWith(".png"));
        assertTrue(Files.exists(getStoredFilePath(imagePath)));
    }

    @Test
    void storeImageAcceptsFileAtMaximumSize()
            throws IOException {

        // given: 실제 PNG 이미지 데이터를 생성
        byte[] pngBytes = createPngBytes();

        assertTrue(pngBytes.length < MAX_FILE_SIZE);

        // PNG 데이터를 5MB까지 확장
        // ImageIO가 PNG 종료 청크 뒤의 추가 데이터를
        // 무시할 수 있는 현재 구현을 이용한 경계값 테스트
        byte[] exactlyFiveMb = Arrays.copyOf(
                pngBytes,
                MAX_FILE_SIZE
        );

        MockMultipartFile file = createFile(
                "maximum.png",
                exactlyFiveMb
        );

        assertEquals(MAX_FILE_SIZE, file.getSize());

        // 현재 ImageIO 환경에서 유효하게 디코딩되는지 확인
        try (InputStream inputStream = file.getInputStream()) {
            assertNotNull(ImageIO.read(inputStream));
        }

        // when
        String imagePath = imageStorageService.storeImage(file);

        // then: 정확히 5MB는 허용
        Path savedFile = getStoredFilePath(imagePath);

        assertTrue(Files.exists(savedFile));
        assertEquals(MAX_FILE_SIZE, Files.size(savedFile));
    }

    // =====================================================
    // 3. null / 빈 파일 / 크기 제한 테스트
    // =====================================================

    @Test
    void storeImageRejectsNullFile() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(null)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsEmptyFile() {

        MockMultipartFile file = createFile(
                "empty.png",
                new byte[0]
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsOversizedFile() {

        // given: 5MB보다 1바이트 큰 파일
        byte[] content = new byte[MAX_FILE_SIZE + 1];

        MockMultipartFile file = createFile(
                "large.png",
                content
        );

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        // then
        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    // =====================================================
    // 4. 원본 파일명 검증 테스트
    // =====================================================

    @Test
    void storeImageRejectsNullFilename() throws IOException {

        MockMultipartFile file = createFile(
                null,
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsBlankFilename() throws IOException {

        MockMultipartFile file = createFile(
                " ",
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsPathTraversal() throws IOException {

        MockMultipartFile file = createFile(
                "../test.png",
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsSlashInFilename() throws IOException {

        MockMultipartFile file = createFile(
                "folder/test.png",
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsBackslashInFilename()
            throws IOException {

        MockMultipartFile file = createFile(
                "folder\\test.png",
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsMissingExtension()
            throws IOException {

        MockMultipartFile file = createFile(
                "test",
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsTrailingDot() throws IOException {

        MockMultipartFile file = createFile(
                "test.",
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsFilenameStartingWithDot()
            throws IOException {

        // given: 확장자 구분자가 첫 글자에 위치
        MockMultipartFile file = createFile(
                ".png",
                createPngBytes()
        );

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        // then
        assertStatus(HttpStatus.BAD_REQUEST, exception);

        assertEquals(
                "이미지 확장자가 올바르지 않습니다.",
                exception.getReason()
        );
    }

    @Test
    void storeImageRejectsUnsupportedExtension()
            throws IOException {

        MockMultipartFile file = createFile(
                "test.gif",
                createPngBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    // =====================================================
    // 5. 실제 이미지 데이터 검증 테스트
    // =====================================================

    @Test
    void storeImageRejectsInvalidImageData() {

        // given: 확장자만 PNG인 일반 문자열
        MockMultipartFile file = createFile(
                "fake.png",
                "this is not an image".getBytes()
        );

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        // then
        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void storeImageRejectsInputStreamFailure()
            throws IOException {

        // given: 이미지 검증 단계에서 IOException 발생
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                createPngBytes()
        ) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("파일 읽기 실패");
            }
        };

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        // then
        assertStatus(HttpStatus.BAD_REQUEST, exception);

        assertEquals(
                "이미지 파일을 읽을 수 없습니다.",
                exception.getReason()
        );
    }

    @Test
    void storeImageShouldReturn500WhenSecondStreamFails()
            throws IOException {

        // given: 첫 번째 스트림은 정상 이미지 검증용
        // 두 번째 스트림은 실제 파일 저장 단계에서 실패
        byte[] pngBytes = createPngBytes();

        MultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                pngBytes
        ) {
            private int streamCallCount = 0;

            @Override
            public InputStream getInputStream() throws IOException {

                streamCallCount++;

                // 첫 번째 호출: 이미지 디코딩 성공
                if (streamCallCount == 1) {
                    return new ByteArrayInputStream(pngBytes);
                }

                // 두 번째 호출: 저장 단계 실패
                throw new IOException("파일 저장 스트림 오류");
            }
        };

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        // then: 실제 저장 단계의 IOException은 500
        assertStatus(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception
        );

        assertEquals(
                "이미지 파일 저장에 실패했습니다.",
                exception.getReason()
        );
    }

    @Test
    void storeImageShouldReturn500WhenDirectoryCreationFails()
            throws IOException {

        // given: 디렉터리 경로에 일반 파일 생성
        Path invalidUploadPath = tempDir.resolve(
                "not-a-directory"
        );

        Files.writeString(
                invalidUploadPath,
                "일반 파일"
        );

        // 일반 파일을 업로드 디렉터리로 설정
        ImageStorageService service = new ImageStorageService(
                invalidUploadPath.toString()
        );

        MockMultipartFile file = createFile(
                "test.png",
                createPngBytes()
        );

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.storeImage(file)
        );

        // then
        assertStatus(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception
        );
    }

    // =====================================================
    // 6. 정상 이미지 삭제 테스트
    // =====================================================

    @Test
    void deleteImageSuccessfully() throws IOException {

        // given: 이미지 업로드
        MockMultipartFile file = createFile(
                "test.png",
                createPngBytes()
        );

        String imagePath = imageStorageService.storeImage(file);

        Path savedFile = getStoredFilePath(imagePath);

        assertTrue(Files.exists(savedFile));

        // when: 이미지 삭제
        imageStorageService.deleteImage(imagePath);

        // then
        assertFalse(Files.exists(savedFile));
    }

    @Test
    void deleteImageDoesNothingForNullPath() {

        // null은 삭제할 대상이 없으므로 정상 종료
        assertDoesNotThrow(
                () -> imageStorageService.deleteImage(null)
        );
    }

    @Test
    void deleteImageDoesNothingForBlankPath() {

        assertDoesNotThrow(
                () -> imageStorageService.deleteImage(" ")
        );
    }

    @Test
    void deleteImageDoesNothingForMissingFile() {

        // deleteIfExists는 파일이 없어도 예외를 발생시키지 않음
        assertDoesNotThrow(
                () -> imageStorageService.deleteImage(
                        IMAGE_PREFIX + "not-found.png"
                )
        );
    }

    // =====================================================
    // 7. 이미지 삭제 경로 검증 테스트
    // =====================================================

    @Test
    void deleteImageRejectsInvalidPrefix() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        "uploads/other/test.png"
                )
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void deleteImageRejectsEmptyFilename() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        IMAGE_PREFIX
                )
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void deleteImageRejectsPathTraversal() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        IMAGE_PREFIX + "../secret.txt"
                )
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void deleteImageRejectsSlashInFilename() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        IMAGE_PREFIX + "folder/test.png"
                )
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    @Test
    void deleteImageRejectsBackslashInFilename() {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        IMAGE_PREFIX + "folder\\test.png"
                )
        );

        assertStatus(HttpStatus.BAD_REQUEST, exception);
    }

    // =====================================================
    // 8. 이미지 삭제 실패 테스트
    // =====================================================

    @Test
    void deleteImageShouldReturn500WhenTargetIsNotEmptyDirectory()
            throws IOException {

        // given: 이미지 파일이 있어야 할 위치에 디렉터리 생성
        Path targetDirectory = tempDir.resolve("image.png");

        Files.createDirectory(targetDirectory);

        // 내부에 파일을 넣어 삭제가 실패하도록 구성
        Files.writeString(
                targetDirectory.resolve("child.txt"),
                "테스트 데이터"
        );

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        IMAGE_PREFIX + "image.png"
                )
        );

        // then
        assertStatus(
                HttpStatus.INTERNAL_SERVER_ERROR,
                exception
        );

        assertEquals(
                "이미지 파일 삭제에 실패했습니다.",
                exception.getReason()
        );
    }
}
