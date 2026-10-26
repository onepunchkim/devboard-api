package com.example.devboard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatImageService {

    // 서버가 허용하는 이미지 확장자
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "webp");

    // application.yml에서 설정을 읽는다.
    private final Path uploadDirectory;
    private final long maxSize;

    public ChatImageService(
            @Value("${chat.image.upload-dir:./uploads/chat}")
            String uploadDir,
            @Value("${chat.image.max-size:5242880}")
            long maxSize
    ) {
        this.uploadDirectory = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();
        this.maxSize = maxSize;
    }

    // 사진 파일을 검증하고 서버에 저장한 뒤 URL을 반환한다.
    public String upload(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 사진을 선택해 주세요.");
        }

        // 업로드 파일 크기를 확인한다.
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    "사진은 최대 5MB까지 업로드할 수 있습니다."
            );
        }

        // 원본 파일 이름에서 확장자를 추출한다.
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("지원하지 않는 사진 파일입니다.");
        }

        String extension = originalFilename
                .substring(originalFilename.lastIndexOf('.') + 1)
                .toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "JPG, JPEG, PNG, WEBP 파일만 업로드할 수 있습니다."
            );
        }

        // 확장자만 믿지 않고 파일의 실제 형식도 확인한다.
        String detectedType = detectImageType(file);

        if (!isMatchingType(extension, detectedType)) {
            throw new IllegalArgumentException(
                    "파일 확장자와 실제 이미지 형식이 일치하지 않습니다."
            );
        }

        // 사용자가 전달한 파일 이름 대신 임의의 이름을 사용한다.
        String storedFilename = UUID.randomUUID() + "." + extension;

        try {
            // 저장 디렉터리가 없으면 생성한다.
            Files.createDirectories(uploadDirectory);

            Path destination = uploadDirectory
                    .resolve(storedFilename)
                    .normalize();

            // 경로가 업로드 디렉터리 밖으로 벗어나지 않도록 검사한다.
            if (!destination.startsWith(uploadDirectory)) {
                throw new IllegalArgumentException("올바르지 않은 파일 경로입니다.");
            }

            // 검증된 파일을 서버 디스크에 저장한다.
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(
                        inputStream,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            // 브라우저에서 접근할 URL을 반환한다.
            return "/uploads/chat/" + storedFilename;

        } catch (IOException e) {
            throw new IllegalStateException("사진 저장에 실패했습니다.", e);
        }
    }

    // 파일 시그니처를 읽어 실제 이미지 형식을 확인한다.
    private String detectImageType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(12);

            // JPEG: FF D8 FF
            if (header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return "jpeg";
            }

            // PNG 시그니처
            if (header.length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A) {
                return "png";
            }

            // WEBP: RIFF....WEBP
            if (header.length >= 12
                    && header[0] == 0x52
                    && header[1] == 0x49
                    && header[2] == 0x46
                    && header[3] == 0x46
                    && header[8] == 0x57
                    && header[9] == 0x45
                    && header[10] == 0x42
                    && header[11] == 0x50) {
                return "webp";
            }

            return "unknown";

        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "사진 파일을 확인할 수 없습니다.", e
            );
        }
    }

    // 확장자와 실제 이미지 형식이 일치하는지 확인한다.
    private boolean isMatchingType(String extension, String detectedType) {
        return switch (extension) {
            case "jpg", "jpeg" -> detectedType.equals("jpeg");
            case "png" -> detectedType.equals("png");
            case "webp" -> detectedType.equals("webp");
            default -> false;
        };
    }
}
