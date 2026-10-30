package com.example.devboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig {

    // application.yml에 설정한 이미지 저장 경로
    @Value("${file.upload-dir}")
    private String uploadDir;

    // 이미지 저장 경로를 스프링 빈으로 등록
    @Bean
    public Path imageUploadPath() {
        // 프로젝트 실행 위치를 기준으로 절대 경로 생성
        return Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize();
    }
}