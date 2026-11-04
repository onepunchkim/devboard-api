
package com.example.devboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatImageServiceTest {

    @TempDir
    Path tempDir;

    private ChatImageService chatImageService;

    // 테스트용 실제 이미지 파일 시그니처
    private static final byte[] JPEG_HEADER = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] WEBP_HEADER = {
            0x52, 0x49, 0x46, 0x46,
            0x00, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50
    };

    @BeforeEach
    void setUp() {
        // 각 테스트마다 별도의 임시 업로드 디렉터리 사용
        chatImageService = new ChatImageService(
                tempDir.toString(),
                1024
        );
    }

    // 테스트용 MultipartFile 생성
    private MockMultipartFile createFile(
            String filename,
            byte[] content
    ) {
        return new MockMultipartFile(
                "file",
                filename,
                "application/octet-stream",
                content
        );
    }

    // ==============================
    // upload() 정상 테스트
    // ==============================

    @Test
    void uploadJpegSuccessfully() {
        MockMultipartFile file =
                createFile("test.jpg", JPEG_HEADER);

        String imageUrl = chatImageService.upload(file);

        assertTrue(imageUrl.startsWith("/uploads/chat/"));
        assertTrue(imageUrl.endsWith(".jpg"));

        String savedFilename =
                imageUrl.substring(imageUrl.lastIndexOf('/') + 1);

        assertTrue(Files.exists(tempDir.resolve(savedFilename)));
    }

    @Test
    void uploadJpegExtensionSuccessfully() {
        MockMultipartFile file =
                createFile("test.jpeg", JPEG_HEADER);

        String imageUrl = chatImageService.upload(file);

        assertTrue(imageUrl.endsWith(".jpeg"));
    }

    @Test
    void uploadPngSuccessfully() {
        MockMultipartFile file =
                createFile("test.png", PNG_HEADER);

        String imageUrl = chatImageService.upload(file);

        assertTrue(imageUrl.endsWith(".png"));
    }

    @Test
    void uploadWebpSuccessfully() {
        MockMultipartFile file =
                createFile("test.webp", WEBP_HEADER);

        String imageUrl = chatImageService.upload(file);

        assertTrue(imageUrl.endsWith(".webp"));
    }

    // ==============================
    // upload() 예외 테스트
    // ==============================

    @Test
    void uploadRejectsNullFile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(null)
        );
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file =
                createFile("empty.jpg", new byte[0]);

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    @Test
    void uploadRejectsFileExceedingMaxSize() {
        byte[] largeContent = new byte[1025];

        MockMultipartFile file =
                createFile("large.jpg", largeContent);

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    @Test
    void uploadRejectsMissingFilename() {
        MockMultipartFile file =
                createFile(null, JPEG_HEADER);

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    @Test
    void uploadRejectsFilenameWithoutExtension() {
        MockMultipartFile file =
                createFile("test", JPEG_HEADER);

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    @Test
    void uploadRejectsUnsupportedExtension() {
        MockMultipartFile file =
                createFile("test.gif", JPEG_HEADER);

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    @Test
    void uploadRejectsMismatchedImageType() {
        // PNG 시그니처인데 확장자는 JPG
        MockMultipartFile file =
                createFile("test.jpg", PNG_HEADER);

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    @Test
    void uploadRejectsUnknownImageType() {
        MockMultipartFile file =
                createFile("test.jpg", new byte[]{
                        0x01, 0x02, 0x03, 0x04
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    @Test
    void uploadRejectsUnsupportedExtensionWithValidHeader() {
        MockMultipartFile file =
                createFile("test.gif", PNG_HEADER);

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    // ==============================
    // detectImageType() 간접 검증
    // ==============================

    @Test
    void uploadRejectsFileWithShortUnknownHeader() {
        MockMultipartFile file =
                createFile("test.png", new byte[]{0x01, 0x02});

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );
    }

    // ==============================
    // readImage() 테스트
    // ==============================

    @Test
    void readImageSuccessfully() throws IOException {
        String filename = UUID.randomUUID() + ".jpg";

        // 임시 업로드 디렉터리에 이미지 파일 생성
        Files.write(
                tempDir.resolve(filename),
                JPEG_HEADER
        );

        byte[] result = chatImageService.readImage(filename);

        assertArrayEquals(JPEG_HEADER, result);
    }

    @Test
    void readImageRejectsNullFilename() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.readImage(null)
        );
    }

    @Test
    void readImageRejectsInvalidFilename() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.readImage("../test.jpg")
        );
    }

    @Test
    void readImageRejectsUnsupportedExtension() {
        String filename = UUID.randomUUID() + ".gif";

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.readImage(filename)
        );
    }

    @Test
    void readImageRejectsMissingFile() {
        String filename = UUID.randomUUID() + ".jpg";

        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.readImage(filename)
        );
    }

    // ==============================
    // getImageContentType() 테스트
    // ==============================

    @Test
    void getImageContentTypeForJpg() {
        assertEquals(
                "image/jpeg",
                chatImageService.getImageContentType("test.jpg")
        );
    }

    @Test
    void getImageContentTypeForJpeg() {
        assertEquals(
                "image/jpeg",
                chatImageService.getImageContentType("test.jpeg")
        );
    }

    @Test
    void getImageContentTypeForPng() {
        assertEquals(
                "image/png",
                chatImageService.getImageContentType("test.png")
        );
    }

    @Test
    void getImageContentTypeForWebp() {
        assertEquals(
                "image/webp",
                chatImageService.getImageContentType("test.webp")
        );
    }

    @Test
    void getImageContentTypeRejectsUnsupportedExtension() {
        assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.getImageContentType("test.gif")
        );
    }

    @Test
    void getImageContentTypeIgnoresUppercaseExtension() {
        assertEquals(
                "image/png",
                chatImageService.getImageContentType("test.PNG")
        );
    }

    // ==============================
    // 파일 읽기 실패 테스트
    // ==============================

    @Test
    void uploadWrapsInputStreamIOException() throws IOException {
        MultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                JPEG_HEADER
        ) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("파일 읽기 실패");
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> chatImageService.upload(file)
        );

        assertEquals(
                "사진 파일을 확인할 수 없습니다.",
                exception.getMessage()
        );
    }
}
