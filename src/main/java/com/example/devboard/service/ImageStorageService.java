package com.example.devboard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    // 허용할 이미지 확장자
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "webp");

    // 이미지 파일 한 개의 최대 크기: 5MB
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    // 이미지 파일을 저장할 기본 경로
    private final Path imageUploadPath;

    public ImageStorageService(
            @Value("${file.upload-dir}") String uploadDir
    ) {
        // 설정한 경로를 절대 경로로 변환
        this.imageUploadPath = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
    }

    // 이미지 파일을 검증하고 서버에 저장
    public String storeImage(MultipartFile file) {

        // 파일이 없는 경우 거부
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "업로드할 이미지 파일이 없습니다."
            );
        }

        // 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일은 5MB 이하여야 합니다."
            );
        }

        // 원본 파일명 확인
        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "파일명이 올바르지 않습니다."
            );
        }

        // 경로 구분자를 포함한 파일명은 거부
        if (originalFilename.contains("/")
                || originalFilename.contains("\\")
                || originalFilename.contains("..")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 파일명입니다."
            );
        }

        // 확장자 추출
        int dotIndex = originalFilename.lastIndexOf(".");

        if (dotIndex <= 0 || dotIndex == originalFilename.length() - 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 확장자가 올바르지 않습니다."
            );
        }

        String extension = originalFilename
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);

        // 허용된 확장자인지 확인
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "JPG, JPEG, PNG, WebP 파일만 업로드할 수 있습니다."
            );
        }

        // 실제 이미지 데이터가 디코딩되는지 확인
        // 확장자만 이미지인 일반 파일을 차단하기 위한 검사
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "올바른 이미지 파일이 아닙니다."
                );
            }
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 파일을 읽을 수 없습니다."
            );
        }

        // UUID를 사용해 중복되지 않는 파일명 생성
        String savedFilename = UUID.randomUUID() + "." + extension;

        // 저장할 파일의 절대 경로 생성
        Path targetPath = imageUploadPath
                .resolve(savedFilename)
                .normalize();

        // 업로드 폴더 밖으로 저장되는 것을 방지
        if (!targetPath.startsWith(imageUploadPath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 파일 경로입니다."
            );
        }

        try {
            // 업로드 폴더가 없으면 생성
            Files.createDirectories(imageUploadPath);

            // 서버에 이미지 저장
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(
                        inputStream,
                        targetPath,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            // DB에 저장할 상대 경로 반환
            return "uploads/post-images/" + savedFilename;

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 파일 저장에 실패했습니다."
            );
        }
    }

    // 서버에 저장된 이미지 파일 삭제
    public void deleteImage(String imagePath) {

        // 경로가 없는 경우 종료
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }

        // DB의 상대 경로가 예상한 형식인지 확인
        String prefix = "uploads/post-images/";

        if (!imagePath.startsWith(prefix)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 이미지 경로입니다."
            );
        }

        // 파일명 부분만 추출
        String filename = imagePath.substring(prefix.length());

        // 파일명에 경로 구분자가 포함되면 거부
        if (filename.isBlank()
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains("..")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 이미지 파일명입니다."
            );
        }

        // 설정된 업로드 폴더 안의 파일만 삭제
        Path targetPath = imageUploadPath
                .resolve(filename)
                .normalize();

        if (!targetPath.startsWith(imageUploadPath)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바르지 않은 이미지 경로입니다."
            );
        }

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 파일 삭제에 실패했습니다."
            );
        }
    }
}