package com.example.devboard.service;

import com.example.devboard.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private ImageStorageService imageStorageService;

    @BeforeEach
    void setUp() {
        // 테스트 전용 임시 디렉터리를 사용한다.
        imageStorageService = new ImageStorageService(
                tempDir.toString()
        );
    }

    // 실제로 디코딩 가능한 PNG 이미지 생성
    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(
                10,
                10,
                BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        ImageIO.write(image, "png", output);

        return output.toByteArray();
    }

    // 테스트용 업로드 파일 생성
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

    // ==============================
    // 정상 업로드 테스트
    // ==============================

    @Test
    void storeImageSuccessfully() throws IOException {
        MockMultipartFile file =
                createFile("test.png", createPngBytes());

        String imagePath = imageStorageService.storeImage(file);

        // DB에 저장할 상대 경로 확인
        assertTrue(
                imagePath.startsWith("uploads/post-images/")
        );
        assertTrue(imagePath.endsWith(".png"));

        // 실제 파일이 저장되었는지 확인
        String filename = imagePath.substring(
                "uploads/post-images/".length()
        );

        assertTrue(
                Files.exists(tempDir.resolve(filename))
        );
    }

    @Test
    void storeImageUsesUniqueFilename() throws IOException {
        MockMultipartFile file =
                createFile("test.png", createPngBytes());

        // 같은 원본 파일을 두 번 업로드한다.
        String first = imageStorageService.storeImage(file);
        String second = imageStorageService.storeImage(file);

        assertNotEquals(first, second);
    }

    @Test
    void storeImageAcceptsUppercaseExtension()
            throws IOException {

        MockMultipartFile file =
                createFile("test.PNG", createPngBytes());

        String imagePath = imageStorageService.storeImage(file);

        assertTrue(imagePath.endsWith(".png"));
    }

    // ==============================
    // 업로드 예외 테스트
    // ==============================

    @Test
    void storeImageRejectsNullFile() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(null)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    @Test
    void storeImageRejectsEmptyFile() {
        MockMultipartFile file =
                createFile("empty.png", new byte[0]);

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsOversizedFile() {
        // 최대 허용 크기인 5MB를 초과한다.
        byte[] content = new byte[5 * 1024 * 1024 + 1];

        MockMultipartFile file =
                createFile("large.png", content);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    @Test
    void storeImageRejectsNullFilename() throws IOException {
        MockMultipartFile file =
                createFile(null, createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsBlankFilename() throws IOException {
        MockMultipartFile file =
                createFile(" ", createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsPathTraversal() throws IOException {
        MockMultipartFile file =
                createFile("../test.png", createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsSlashInFilename() throws IOException {
        MockMultipartFile file =
                createFile("folder/test.png", createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsBackslashInFilename()
            throws IOException {

        MockMultipartFile file =
                createFile("folder\\test.png", createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsMissingExtension()
            throws IOException {

        MockMultipartFile file =
                createFile("test", createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsTrailingDot() throws IOException {
        MockMultipartFile file =
                createFile("test.", createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsUnsupportedExtension()
            throws IOException {

        MockMultipartFile file =
                createFile("test.gif", createPngBytes());

        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );
    }

    @Test
    void storeImageRejectsInvalidImageData() {
        MockMultipartFile file = createFile(
                "fake.png",
                "this is not an image".getBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    @Test
    void storeImageRejectsInputStreamFailure()
            throws IOException {

        // 파일을 읽는 과정에서 IOException 발생
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

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.storeImage(file)
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    // ==============================
    // 정상 삭제 테스트
    // ==============================

    @Test
    void deleteImageSuccessfully() throws IOException {
        MockMultipartFile file =
                createFile("test.png", createPngBytes());

        String imagePath = imageStorageService.storeImage(file);

        String filename = imagePath.substring(
                "uploads/post-images/".length()
        );

        Path savedFile = tempDir.resolve(filename);

        assertTrue(Files.exists(savedFile));

        // 이미지 파일 삭제
        imageStorageService.deleteImage(imagePath);

        assertFalse(Files.exists(savedFile));
    }

    @Test
    void deleteImageDoesNothingForNullPath() {
        // null이면 예외 없이 종료해야 한다.
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
        // 없는 파일은 deleteIfExists로 처리한다.
        assertDoesNotThrow(
                () -> imageStorageService.deleteImage(
                        "uploads/post-images/not-found.png"
                )
        );
    }

    // ==============================
    // 삭제 경로 검증 테스트
    // ==============================

    @Test
    void deleteImageRejectsInvalidPrefix() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        "uploads/other/test.png"
                )
        );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    @Test
    void deleteImageRejectsEmptyFilename() {
        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        "uploads/post-images/"
                )
        );
    }

    @Test
    void deleteImageRejectsPathTraversal() {
        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        "uploads/post-images/../secret.txt"
                )
        );
    }

    @Test
    void deleteImageRejectsSlashInFilename() {
        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        "uploads/post-images/folder/test.png"
                )
        );
    }

    @Test
    void deleteImageRejectsBackslashInFilename() {
        assertThrows(
                ResponseStatusException.class,
                () -> imageStorageService.deleteImage(
                        "uploads/post-images/folder\\test.png"
                )
        );
    }
}
